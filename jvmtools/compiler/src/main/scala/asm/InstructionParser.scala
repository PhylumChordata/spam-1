package asm

import AddressMode.{DIRECT, REGISTER}
import asm.ConditionMode.{Invert, Standard}

import scala.language.postfixOps
import scala.util.parsing.combinator._

object AddressMode extends Enumeration {
  type Mode = Value
  val DIRECT, REGISTER = Value
}

// expr "calculator" code taken from https://www.scala-lang.org/api/2.12.8/scala-parser-combinators/scala/util/parsing/combinator/RegexParsers.html
trait EnumParserOps  {
  self: JavaTokenParsers=>
  def enumToParser[A <: E](e: Seq[A]): Parser[A] = {
    // reverse sorted to put longer operators ahead of shorter ones otherwise shorter ones gobble
    val longestFirst: Seq[A] = e.sortBy(_.enumName).reverse
    longestFirst map { m =>
      literal(m.enumName) ^^^ m
    } reduceLeft {
      _ | _
    }
  }

}

trait InstructionParser extends EnumParserOps with JavaTokenParsers {
  self: Lines with Knowing with Devices =>

  var dataAddress = 0

  //  override val whiteSpace =  """[ \t\f\x0B]++""".r // whitespace not including line endings

  def aluop: Parser[AluOp] = {
    val shortAluOps = {
      // reverse sorted to put longer operators ahead of shorter ones otherwise shorter ones gobble
      val reverseSorted = AluOp.values.filter(_.isAbbreviated).sortBy(x => x.abbrev).reverse.toList
      reverseSorted map { m =>
        literal(m.abbrev) ^^^ m
      } reduceLeft {
        _ | _
      }
    }
    val longAluOps = enumToParser(AluOp.values)
    longAluOps | shortAluOps
  }

  def adev: Parser[ADevice] = enumToParser(ADevice.values)

  def bdev: Parser[BDevice] = enumToParser(BDevice.values)

  def bdevonly: Parser[BOnlyDevice] = enumToParser(BOnlyDevice.values)

  def tdev: Parser[TDevice] = enumToParser(TDevice.values)

  def controlCode: Parser[Control] = enumToParser(Control.values)

  def condition: Parser[Condition] = ("!"?) ~ (controlCode?) ^^ {
    case mode ~ ctrl =>
      Condition(mode.map(_ => Invert).getOrElse(Standard), ctrl.getOrElse(Control._A))
  }


  def name: Parser[String] = "[a-zA-Z][a-zA-Z0-9_]*".r ^^ (a => a)

  def pcref: Parser[Known[KnownInt]] = """.""" ^^ (v => Known("pc", pc))

  def dec: Parser[Known[KnownInt]] =
    """-?\d+""".r ^^ { v =>
      val vi = v.toInt
      Known("", vi)
    }

  def char: Parser[Known[KnownInt]] = "'" ~> ".".r <~ "'" ^^ { v =>
    val i = v.codePointAt(0)
    if (i > 127) throw new RuntimeException(s"asm error: character '$v' codepoint $i is outside the 0-127 range")
    Known("", i.toByte)
  }

  def hex: Parser[Known[KnownInt]] = "$" ~ "[0-9a-hA-H]+".r ^^ { case _ ~ v => Known("$" + v, Integer.valueOf(v, 16)) }

  def bin: Parser[Known[KnownInt]] = "%" ~ "[01]+".r ^^ { case _ ~ v => Known("%" + v, Integer.valueOf(v, 2)) }

  def oct: Parser[Known[KnownInt]] = "@" ~ "[0-7]+".r ^^ { case _ ~ v => Known("@" + v, Integer.valueOf(v, 8)) }

  def labelAddr: Parser[IsKnowable[KnownInt]] = ":" ~ name ^^ { case _ ~ v => forwardReference(v) }

  def labelLen: Parser[IsKnowable[KnownInt]] = "len(" ~ ":" ~> name <~ ")" ^^ {
    n =>
      def lookup(): Option[KnownInt] = {
        val maybeKnow = labels.get(n)
        val option: Option[_ <: KnownValue] = maybeKnow.flatMap(_.getVal)

        option.map {
          case KnownByteArray(_, b) =>
            KnownInt(b.length)
          case KnownInt(v) =>
            if (v < 0) {
              sys.error(s"asm error: len(...) is valid only for positive values, but got len( $v ) ")
            }
            val powerOf2ZeroOffset = Math.log(1 + v.toDouble) / Math.log(2.toDouble)
            val bytesNeeded = Math.ceil(powerOf2ZeroOffset / 8).toInt
            val n = Math.max(1, bytesNeeded)
            KnownInt(n)
        }
      }

      Knowable(s"len(:$n)", () =>
        lookup()
      )
  }

  def hiByte: Parser[IsKnowable[KnownInt]] = "<" ~> expr ^^ { e: Know[KnownInt] =>
    UniKnowable[KnownInt](() => e, i => KnownInt((i.value >> 8) & 0xff), "HI<")
  }

  def loByte: Parser[IsKnowable[KnownInt]] = ">" ~> expr ^^ { e: Know[KnownInt] =>
    UniKnowable[KnownInt](() => e, i => KnownInt(i.value & 0xff), "LO>")
  }

  def factor: Parser[IsKnowable[KnownInt]] = labelLen | char | pcref | dec | hex | bin | oct | "(" ~> expr <~ ")" | hiByte | loByte | labelAddr

  // picks up literal numeric expressions where both sides are expr
  def expr: Parser[IsKnowable[KnownInt]] = factor ~ rep("*" ~ factor | "/" ~ factor | "&" ~ factor | "|" ~ factor | "+" ~ factor | "-" ~ factor) ^^ {
    case number ~ list =>
      list.foldLeft(number) {
        case (x, "*" ~ y) => BiKnowable[KnownInt, KnownInt, KnownInt](() => x, () => y, _ * _, "*")
        case (x, "+" ~ y) => BiKnowable[KnownInt, KnownInt, KnownInt](() => x, () => y, _ + _, "+")
        case (x, "/" ~ y) => BiKnowable[KnownInt, KnownInt, KnownInt](() => x, () => y, _ / _, "/")
        case (x, "-" ~ y) => BiKnowable[KnownInt, KnownInt, KnownInt](() => x, () => y, _ - _, "-")
        case (x, "&" ~ y) => BiKnowable[KnownInt, KnownInt, KnownInt](() => x, () => y, _ & _, "&")
        case (x, "|" ~ y) => BiKnowable[KnownInt, KnownInt, KnownInt](() => x, () => y, _ | _, "|")
        case (x, op ~ y) => sys.error(s"sw error : missing handler for op '$op' for operand $x and $y")
      }
  }

  def ramDirect: Parser[RamDirect] = "[" ~ expr ~ "]" ^^ { case _ ~ v ~ _ => RamDirect(v) }

  def debug: Parser[Debug] = ";;" ~> ".*".r ^^ (a => Debug(a))
  def comment: Parser[Comment] = ";" ~> ".*".r ^^ (a => Comment(a))

  def targets: Parser[TExpression] = tdev | ramDirect

  def bdevices: Parser[BExpression] = bdev | ramDirect

  def bdeviceOrRamDirect: Parser[BOnlyDevice] = bdevonly | ramDirect


  def label: Parser[Label] = name ~ ":" ^^ {
    case n ~ _ =>
      rememberKnown(n, Known(n, KnownInt(pc)))
      Label(n)
  }

  def eqInstruction: Parser[EquInstruction] = (name <~ ":" ~ "EQU") ~ expr ^^ {
    case n ~ konst =>
      rememberKnown(n, konst)
      EquInstruction(n, konst)
  }

  // amended vs 'stringLiteral' to include the short form \0
  def quotedString: Parser[String] = ("\"" + """([^"\x01-\x1F\x7F\\]|\\[\\'"0bfnrt]|\\u[a-fA-F0-9]{4})*""" + "\"").r ^^ {
    s =>
      val withoutQuotes = s.stripPrefix("\"").stripSuffix("\"")
      val str = org.apache.commons.text.StringEscapeUtils.unescapeJava(withoutQuotes)
      str
  }

  def strInstruction: Parser[List[Line]] = (name <~ ":" ~ "STR") ~ quotedString ^^ {
    case a ~ b =>
      val bytes = b.getBytes("UTF-8")

      val stored = rememberKnown(a, Known(a, KnownByteArray(dataAddress, bytes.toList)))
      dataAddress = stored.knownVal.value

      Label(a) +: bytes.map { c => {
        val ni = inst(RamDirect(Known("", dataAddress)), ADevice.NU, AluOp.PASS_B, BDevice.IMMED, Some(Condition.Default), Known("", c))
        dataAddress += 1
        ni
      }
      }.toList
  }

  def bytesInstruction: Parser[List[Line]] = (name <~ ":" ~ "BYTES" ~ "[") ~ repsep(expr, ",") <~ "]" ^^ {
    case n ~ expr =>
      if (expr.isEmpty) {
        sys.error(s"asm error: BYTES expression with label '$n' must have at least one byte but none were defined")
      }
      val exprs: List[Know[KnownInt]] = expr

      val ints: List[Int] = exprs.map(_.getVal.get.value)

      ints.filter { x =>
        x < Byte.MinValue || x > 255
      }.foreach(x => sys.error(s"asm error: $x evaluates as out of range ${Byte.MinValue} to 255"))

      rememberKnown(n, Known(n, KnownByteArray(pc, ints.map(_.toByte))))

      Label(n) +: ints.map {
        c => {
          // c.toByte will render between -128 and  +127
          // then name "c" will render as whatever int value was actually presented in the code (eg when c=255 then toByte = -1 )
          val immed = Known(f"${c.toByte}%02X", c.toByte)

          val ni = inst(RamDirect(Known("", dataAddress)), ADevice.NU, AluOp.PASS_B, BDevice.IMMED, Some(Condition.Default), immed)
          dataAddress += 1
          ni
        }
      }
  }

  private def inst(t: TExpression, a: ADevice, op: AluOp, b: BExpression, f: Option[Condition], immed: Know[KnownInt]): Instruction = {
    val defaultCont = Condition(ConditionMode.Standard, Control._A)

    (t, b) match {
      case (t: TDevice, b: BDevice) =>
        Instruction(t, a, b, op, f.getOrElse(defaultCont), REGISTER, Irrelevant(), immed)
      case (t: TDevice, RamDirect(addr)) =>
        Instruction(t, a, BDevice.RAM, op, f.getOrElse(defaultCont), DIRECT, addr, immed)
      case (RamDirect(addr), b: BDevice) =>
        Instruction(TDevice.RAM, a, b, op, f.getOrElse(defaultCont), DIRECT, addr, immed)
      case (RamDirect(_), RamDirect(_)) =>
        sys.error(s"illegal instruction: target '$t' and source '$b' cannot both be RAM")
    }
  }

  def abInstruction: Parser[Line] = (targets <~ "=") ~ adev ~ aluop ~ bdevices ~ (condition ?) ^^ {
    case t ~ a ~ op ~ b ~ f =>
      inst(t, a, op, b, f, Irrelevant())
  }

  def abInstructionImmed: Parser[Line] = (targets <~ "=") ~ adev ~ aluop ~ expr ~ (condition ?) ^^ {
    case t ~ a ~ op ~ immed ~ f =>
      inst(t, a, op, BDevice.IMMED, f, immed)
  }

  def bInstruction: Parser[Line] = (targets <~ "=") ~ bdeviceOrRamDirect ~ (condition ?) ^^ {
    case t ~ b ~ f =>
      inst(t, ADevice.NU, AluOp.PASS_B, b, f, Irrelevant())
  }

  def bInstructionImmed: Parser[Line] = (targets <~ "=") ~ expr ~ (condition ?) ^^ {
    case t ~ immed ~ f =>
      inst(t, ADevice.NU, AluOp.PASS_B, BDevice.IMMED, f, immed)
  }

  def aInstruction: Parser[Line] = (targets <~ "=") ~ adev ~ (condition ?) ^^ {
    case t ~ a ~ f =>
      inst(t, a, AluOp.PASS_A, BDevice.NU, f, Irrelevant())
  }

  def line: Parser[List[Line]] = (strInstruction | bytesInstruction | eqInstruction | bInstruction | abInstructionImmed | abInstruction | aInstruction | bInstructionImmed | debug | comment | label) ^^ {
    case x: List[_] => x.asInstanceOf[List[Line]]
    case x: Line => List(x)
  }

  def lines: Parser[List[Line]] = line ~ (line *) <~ "END" ^^ {
    case a ~ b =>
      a ++ b.flatten
  }
}