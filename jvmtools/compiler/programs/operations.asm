

; define a constant called SOMEVAR using a constant expression
; forward references to label addresses are permitted

SOMEVAR:    EQU ($0100 + %1010 + $f + 1+2+(:LABEL2+33)) ; some arbitrarily complicated constant expression

; grab the top and botton bytes of the constant SOMEVAR into two constants

TOPBYTE:    EQU <:SOMEVAR      
BOTBYTE:    EQU >:SOMEVAR

; demo setting registers to constants

            REGA = $ff                          ; set A to the constant hex ff but do not set processor status flags
            REGB = $ff    _S                     ; set A to the constant hex ff and set the processor status flags

; registers can be set to results of ALU operations

LABEL1:     REGA = REGB   _C_S                   ; if Carry is set then update A to value of B and set the flags
            REGA = REGA A_PLUS_B REGC           ; set A = A + B but do not set flags
            REGA = REGB A_MINUS_B [:SOMEVAR]    ; set B to the data at RAM location :SOMEVAR
LABEL2:
            REGA = :TOPBYTE                     ; set A to the constant

; unconditional jump to whatever SOMEVAR was pointing to 

            PCHITMP = :TOPBYTE                  ; prepare the top PC register for a jump
            PC      = :BOTBYTE                  ; execute the jump to the location defined by {TOPBYTE:PCHITMP}

END
