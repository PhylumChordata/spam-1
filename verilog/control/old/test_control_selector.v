//#!/usr/bin/iverilog -Ttyp -Wall -g2012 -gspecify -o test.vvp 
`include "./control_selector.v"
`include "../lib/assertion.v"

// verilator lint_off ASSIGNDLY
// verilator lint_off STMTDLY

`timescale 1ns/100ps
`default_nettype none


module test_select();

        function [4:0] rol(input [4:0] x);
            logic [4:0] rol;
            rol = {x[3:0], x[4]};
        endfunction

	logic [7:0] hi_rom;
	logic _flag_z, _flag_c, _flag_o, _flag_eq, _flag_ne, _flag_gt, _flag_lt;
        logic _uart_in_ready, _uart_out_ready;

        logic _rom_out, _ram_out, _alu_out, _uart_out;
            
        logic [4:0] device_in;

        logic force_alu_op_to_passx;
        logic force_x_val_to_zero;
	logic _ram_zp;


	control_selector ctrl(
            .hi_rom, 

            ._rom_out, ._ram_out, ._alu_out, ._uart_out,
            .device_in,

            .force_alu_op_to_passx,
            .force_x_val_to_zero,
            ._ram_zp
	);
    

    initial begin

        `ifndef verilator

        $dumpfile("dumpfile.vcd");
        $dumpvars(0,  
            hi_rom, 
            _rom_out, _ram_out, _alu_out, _uart_out,
            device_in,
            
            force_x_val_to_zero,
            force_alu_op_to_passx,
            
            _ram_zp
        );

/*
        $display ("");
        $display ($time, "  %8s  %3s %3s %3s %3s %3s %3s %3s  %3s %3s  %5s %5s %5s %5s   %5s %5s %5s %5s  %8s %6s %5s  %5s %14s %12s %7s", 
                                                "hi",
                                                "nZ", "nC", "nO", "nEQ", "nNE", "nGT", "nLT",
                                                "nDI", "nDO", 
                                                "nRom","nRam","nAlu","nUart",
                                                "nRam", "nMarlo", "nMarhi", "nUart", 
                                                "nPchitmp", "nPclo", "nPc",
                                                "nRegin",
                                                "nForceXvalTo0", 
                                                "forceAluToA", 
                                                "nRamZp");
        $monitor ($time, "  %08b  %3b %3b %3b %3b %3b %3b %3b  %3b %3b  %5b %5b %5b %5b   %5b %5b %5b %5b  %8b %6b %5b  %5b %14b %12b %7b", 
            hi_rom, 
            _flag_z, _flag_c, _flag_o, _flag_eq, _flag_ne, _flag_gt, _flag_lt,
            _uart_in_ready, _uart_out_ready,

            _rom_out, _ram_out, _alu_out, _uart_out,
            _ram_in, _marlo_in, _marhi_in, _uart_in,
            _pchitmp_in, _pclo_in, _pc_in,
            
            _reg_in,
            force_x_val_to_zero,
            force_alu_op_to_passx,
            
            _ram_zp
        );
 */       
        `endif
    end

    initial begin
        
        parameter T      = 1'b1;
        parameter F      = 1'b0;

        parameter pad6      = 6'b000000;
        parameter pad5      = 5'b00000;
        parameter pad4      = 4'b0000;
        

        // all routes to belect
        parameter [2:0] op_DEV_eq_ROM_sel = 0;
        parameter [2:0] op_DEV_eq_RAM_sel = 1;
        parameter [2:0] op_DEV_eq_RAMZP_sel = 2;
        parameter [2:0] op_DEV_eq_UART_sel = 3;
        parameter [2:0] op_NONREG_eq_OPREGY_sel = 4;
        parameter [2:0] op_REGX_eq_ALU_sel = 5;
        parameter [2:0] op_RAMZP_eq_REG_sel = 6;
        parameter [2:0] op_RAMZP_eq_UART_sel = 7;

        // because MSB
        parameter [4:0] idx_RAM_sel      = 0;
        parameter [4:0] idx_MARLO_sel    = 1;
        parameter [4:0] idx_MARHI_sel    = 2;
        parameter [4:0] idx_UART_sel     = 3;
        parameter [4:0] idx_PCHITMP_sel  = 4;
        parameter [4:0] idx_PCLO_sel     = 5;
        parameter [4:0] idx_PC_sel       = 6;
        parameter [4:0] idx_JMPO_sel     = 7;

        parameter [4:0] idx_JMPZ_sel     = 8;
        parameter [4:0] idx_JMPC_sel     = 9;
        parameter [4:0] idx_JMPDI_sel    = 10;
        parameter [4:0] idx_JMPDO_sel    = 11;
        parameter [4:0] idx_JMPEQ_sel    = 12;
        parameter [4:0] idx_JMPNE_sel    = 13;
        parameter [4:0] idx_JMPGT_sel    = 14;
        parameter [4:0] idx_JMPLT_sel    = 15;

        parameter [4:0] idx_REGA_sel     = 16;
        parameter [4:0] idx_REGP_sel     = 31;

        // all devices to select
        parameter [4:0] dev_RAM_sel      = rol(idx_RAM_sel);
        parameter [4:0] dev_MARLO_sel    = rol(idx_MARLO_sel);
        parameter [4:0] dev_MARHI_sel    = rol(idx_MARHI_sel);
        parameter [4:0] dev_UART_sel     = rol(idx_UART_sel);
        parameter [4:0] dev_PCHITMP_sel  = rol(idx_PCHITMP_sel);
        parameter [4:0] dev_PCLO_sel     = rol(idx_PCLO_sel);
        parameter [4:0] dev_PC_sel       = rol(idx_PC_sel);
        parameter [4:0] dev_JMPO_sel     = rol(idx_JMPO_sel);

        parameter [4:0] dev_JMPZ_sel     = rol(idx_JMPZ_sel);
        parameter [4:0] dev_JMPC_sel     = rol(idx_JMPC_sel);
        parameter [4:0] dev_JMPDI_sel    = rol(idx_JMPDI_sel);
        parameter [4:0] dev_JMPDO_sel    = rol(idx_JMPDO_sel);
        parameter [4:0] dev_JMPEQ_sel    = rol(idx_JMPEQ_sel);
        parameter [4:0] dev_JMPNE_sel    = rol(idx_JMPNE_sel);
        parameter [4:0] dev_JMPGT_sel    = rol(idx_JMPGT_sel);
        parameter [4:0] dev_JMPLT_sel    = rol(idx_JMPLT_sel);

        parameter [4:0] dev_REGA_sel     = rol(idx_REGA_sel);
        parameter [4:0] dev_REGP_sel     = rol(idx_REGP_sel);
        
        
        parameter [4:0] ALU_ZERO_VAL     = 0;
        parameter [4:0] ALU_PASSX        = 0;
        
        parameter zp_off_sel = 1'b1;
        parameter zp_on_sel = 1'b0;
        

        $display("testing select");
    // ===========================================================================

`include "./generated_tests.v"


end

endmodule : test_select