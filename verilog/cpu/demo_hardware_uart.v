`include "cpu.v"
`include "../lib/assertion.v"
`include "psuedo_assembler.sv"
`include "control_lines.v"
`timescale 1ns/1ns



`define SEMICOLON ;
`define COMMA ,

module test();

    import alu_ops::*;
    import control::*;

    function string disasm([47:0] INSTRUCTION);
         reg [4:0] i_aluop;
         reg [3:0] i_target;
         reg [2:0] i_srca;
         reg [2:0] i_srcb;
         reg [3:0] i_cond;
         reg i_flag;
         reg [2:0] i_nu;
         reg i_amode;
         reg [23:8] i_addr ;
         reg [7:0] i_immed;
    begin
         i_aluop = INSTRUCTION[47:43]; 
         i_target = INSTRUCTION[42:39]; 
         i_srca = INSTRUCTION[38:36]; 
         i_srcb = INSTRUCTION[35:33]; 
         i_cond = INSTRUCTION[32:29]; 
         i_flag = INSTRUCTION[28]; 
         i_nu   = INSTRUCTION[27:25]; 
         i_amode= INSTRUCTION[24]; 
         i_addr = INSTRUCTION[23:8]; 
         i_immed= INSTRUCTION[7:0]; 
        disasm = $sformatf("aluop:%-10ss(%d)  target:%-10s(%d) a:%-5s(%d)  b:%-10s(%d)  cond:%s(%d) setf:%s amode:%s addr:%4x immed8:%2x", 
                    aluopName(i_aluop), i_aluop,
                    tdevname(i_target), i_target,
                    adevname(i_srca),  i_srca,
                    bdevname(i_srcb),  i_srcb,
                    condname(i_cond),  i_cond,
                    (i_flag? "NOSET" : "SET"), 
                    (i_amode?  "DIR": "REG"), 
                    i_addr, 
                    i_immed); 
    end 
    endfunction

   `include "../lib/display_snippet.sv"

    logic clk=0;
    cpu CPU(1'b0, clk);

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    // TESTS ===========================================================================================
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    localparam MAX_PC=65536;
    `DEFINE_CODE_VARS(MAX_PC)


    logic [47:0] data =0;
    int addr;
    string rom1 = "roms/pattern1.rom";
    string rom2 = "roms/pattern2.rom";
    string rom3 = "roms/pattern3.rom";
    string rom4 = "roms/pattern4.rom";
    string rom5 = "roms/pattern5.rom";
    string rom6 = "roms/pattern6.rom";
    int n_file1;
    int n_file2;
    int n_file3;
    int n_file4;
    int n_file5;
    int n_file6;

    integer icount=0;

    
    wire [7:0] cH = "H";
    wire [7:0] cE = "e";
    wire [7:0] cL = "l";
    wire [7:0] cO = "o";

    string hello = "Hello!";
    string bye = "Hello!";
    int idx;
    int loop_addr;
    int write_addr;

    `define READ 20
    `define READ_LOOP (`READ+1)
    `define WRITE_HELLO 128
    initial begin

        icount=0;

        `DEV_EQ_IMMED8(icount, rega, 1); icount++;
        `DEV_EQ_IMMED8(icount, regb, 2); icount++;
        
        `INSTRUCTION_S(icount, pchitmp, not_used, immed, B, A,  `SET_FLAGS, `NA_AMODE, 'z, (`READ_LOOP>>8)); icount++;
        `INSTRUCTION_S(icount, pc,      not_used, immed, B, DI, `SET_FLAGS, `NA_AMODE, 'z, (`READ_LOOP)); icount++;
        `JMP_IMMED16(icount, 0); icount+=2; 


        // do while DI 
        icount=`READ_LOOP;
        `INSTRUCTION_S(icount, rega,    uart,     not_used, A, A,  `SET_FLAGS, `NA_AMODE, 'z, 'z); icount++;
        `INSTRUCTION_S(icount, pchitmp, not_used, immed,    B, A,  `SET_FLAGS, `NA_AMODE, 'z, (`READ_LOOP>>8)); icount++;
        `INSTRUCTION_S(icount, pc,      not_used, immed,    B, DI, `SET_FLAGS, `NA_AMODE, 'z, (`READ_LOOP)); icount++;
        `JMP_IMMED16(icount, `WRITE_HELLO); icount+=2; 


        icount=`WRITE_HELLO;
        for (idx=0; idx<hello.len(); idx++) begin
            loop_addr = icount;
            write_addr = icount+4;
            `INSTRUCTION_S(icount, pchitmp, not_used, immed,    B, A,  `SET_FLAGS, `NA_AMODE, 'z, (write_addr>>8)); icount++;
            `INSTRUCTION_S(icount, pc,      not_used, immed,    B, DO, `SET_FLAGS, `NA_AMODE, 'z, (write_addr)); icount++;
            `JMP_IMMED16(icount, loop_addr); icount+=2; 

            `INSTRUCTION_S(icount, uart,    not_used, immed,    B, A,  `SET_FLAGS, `NA_AMODE, 'z, hello[idx]); icount++;
        end
        `JMP_IMMED16(icount, 0); icount+=2; 


        //`JMP_IMMED16(icount, 0); icount+=2; 
        n_file1 = $fopen(rom1, "wb");
        n_file2 = $fopen(rom2, "wb");
        n_file3 = $fopen(rom3, "wb");
        n_file4 = $fopen(rom4, "wb");
        n_file5 = $fopen(rom5, "wb");
        n_file6 = $fopen(rom6, "wb");

        for (addr=0; addr < icount; addr++) begin
            //$display("CODE : %-s" , CODE_NUM[addr]);

            // little endian 
            data = `ROM(addr);

            #1000

            $fwrite(n_file1, "%c", data[7:0]);
            $fwrite(n_file2, "%c", data[15:8]);
            $fwrite(n_file3, "%c", data[23:16]);
            $fwrite(n_file4, "%c", data[31:24]);
            $fwrite(n_file5, "%c", data[39:32]);
            $fwrite(n_file6, "%c", data[47:40]);

            $display("written %d", addr, " = %8b %8b %8b %8b %8b %8b(%c)", 
                data[47:40],
                data[39:32],
                data[31:24],
                data[23:16],
                data[15:8],
                data[7:0],
                printable(data[7:0]),
                );
            $display("CODE : %-s" , disasm(data), "(%c)(%d)",
                printable(data[7:0]),
                data[7:0]
             );
            $display("");
        end    

        $fclose(n_file1);
        $fclose(n_file2);
        $fclose(n_file3);
        $fclose(n_file4);
        $fclose(n_file5);
        $fclose(n_file6);

        $display("DONE");
        $finish();
    end

    function [7:0] printable([7:0] c);
        if (c == 0) return 32;
        else if ($isunknown(c)) return 32; 
        else if (c < 32 ) return 32; 
        else if (c >= 128) return 32;
        return c;
    endfunction

endmodule : test