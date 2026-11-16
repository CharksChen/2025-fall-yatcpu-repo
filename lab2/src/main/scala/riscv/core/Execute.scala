// Copyright 2021 Howard Lau
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/*
执行模块的思考顺序：ALU仍负责普通算术/跳转；Lab2新增CSR运算。CSR旧值经WB写回rd，CSR新值经本模块输出到CSR模块。请区分两条数据流。
*/
package riscv.core

import chisel3._
import chisel3.util.{Cat, MuxLookup}
import riscv.Parameters

class Execute extends Module {
  val io = IO(new Bundle {
    val instruction = Input(UInt(Parameters.InstructionWidth))
    val instruction_address = Input(UInt(Parameters.AddrWidth))
    val reg1_data = Input(UInt(Parameters.DataWidth))
    val reg2_data = Input(UInt(Parameters.DataWidth))
    val immediate = Input(UInt(Parameters.DataWidth))
    val aluop1_source = Input(UInt(1.W))
    val aluop2_source = Input(UInt(1.W))
    // 输入旧CSR值：CSRRS/CSRRC需要与源操作数做位运算。
    val csr_reg_read_data = Input(UInt(Parameters.DataWidth))

    val mem_alu_result = Output(UInt(Parameters.DataWidth))
    // 输出准备写回CSR的新值，不是写回普通寄存器rd的旧值。
    val csr_reg_write_data = Output(UInt(Parameters.DataWidth))
    val if_jump_flag = Output(Bool())
    val if_jump_address = Output(UInt(Parameters.DataWidth))
  })

  // 从32位指令取[6:0] opcode、[14:12] funct3；CSR指令属于SYSTEM(opcode=0x73)。
  val opcode = io.instruction(6, 0)
  val funct3 = io.instruction(14, 12)
  val funct7 = io.instruction(31, 25)

  // 原有ALU负责普通指令；CSR位运算可以在EX直接由组合逻辑实现。
  val alu = Module(new ALU)
  val alu_ctrl = Module(new ALUControl)

  alu_ctrl.io.opcode := opcode
  alu_ctrl.io.funct3 := funct3
  alu_ctrl.io.funct7 := funct7
  alu.io.func := alu_ctrl.io.alu_funct
  // 选择普通ALU第一个操作数：PC或者rs1。
  alu.io.op1 := Mux(
    io.aluop1_source === ALUOp1Source.InstructionAddress,
    io.instruction_address,
    io.reg1_data,
  )
  alu.io.op2 := Mux(
    io.aluop2_source === ALUOp2Source.Immediate,
    io.immediate,
    io.reg2_data,
  )
  // 该跳转判断结果会被CLINT读取，以确定硬件中断的恢复地址。
  io.if_jump_flag := opcode === Instructions.jal ||
    (opcode === Instructions.jalr) ||
    (opcode === InstructionTypes.B) && MuxLookup(
      funct3,
      false.B,
      IndexedSeq(
        InstructionsTypeB.beq -> (io.reg1_data === io.reg2_data),
        InstructionsTypeB.bne -> (io.reg1_data =/= io.reg2_data),
        InstructionsTypeB.blt -> (io.reg1_data.asSInt < io.reg2_data.asSInt),
        InstructionsTypeB.bge -> (io.reg1_data.asSInt >= io.reg2_data.asSInt),
        InstructionsTypeB.bltu -> (io.reg1_data.asUInt < io.reg2_data.asUInt),
        InstructionsTypeB.bgeu -> (io.reg1_data.asUInt >= io.reg2_data.asUInt)
      )
    )
  io.if_jump_address := io.immediate + Mux(opcode === Instructions.jalr, io.reg1_data, io.instruction_address)
  // 普通算术结果照旧送往MEM/WB，不要用CSR新值覆盖它。
  io.mem_alu_result := alu.io.result
  // 实验留白：CSRRW(I)新值=源操作数；CSRRS(I)=旧值|源；CSRRC(I)=旧值&~源。funct3高位区分寄存器版与立即数版；zimm取instruction[19:15]零扩展。
  //lab2(CLINTCSR)
  /*
  io.csr_reg_write_data :=
  */
}
