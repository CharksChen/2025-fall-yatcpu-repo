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
思考顺序：拿到哪些数据 → 选择 ALU 输入 → 决定执行什么运算 → 输出结果并判断跳转
刚学过的译码模块决定“选哪一路”；现在执行模块根据这些控制信号，真正把数据送进运算电路
*/


package riscv.core

import chisel3._
import chisel3.util.{Cat, MuxLookup}
import riscv.Parameters

class Execute extends Module {
  val io = IO(new Bundle {
    val instruction = Input(UInt(Parameters.InstructionWidth))//当前机器指令
    val instruction_address = Input(UInt(Parameters.AddrWidth))//当前机器指令的地址,即PC
    val reg1_data = Input(UInt(Parameters.DataWidth))//寄存器1的数据
    val reg2_data = Input(UInt(Parameters.DataWidth))//寄存器2的数据
    val immediate = Input(UInt(Parameters.DataWidth))//立即数
    val aluop1_source = Input(UInt(1.W))//ALU操作数1的来源选择信号，0表示来自寄存器1，1表示来自指令地址
    val aluop2_source = Input(UInt(1.W))//ALU操作数2的来源选择信号，0表示来自寄存器2，1表示来自立即数

    val mem_alu_result = Output(UInt(Parameters.DataWidth))//ALU运算结果，供访存模块使用
    val if_jump_flag = Output(Bool())//是否跳转的标志，供译码模块使用
    val if_jump_address = Output(UInt(Parameters.DataWidth))//跳转地址，供译码模块使用
  })

  val opcode = io.instruction(6, 0)
  val funct3 = io.instruction(14, 12)
  val funct7 = io.instruction(31, 25)
  val rd = io.instruction(11, 7)
  val uimm = io.instruction(19, 15)

  val alu = Module(new ALU)//	根据操作类型和两个输入，算出结果
  val alu_ctrl = Module(new ALUControl)//  根据指令的opcode、funct3、funct7，决定ALU要执行的具体运算类型

  alu_ctrl.io.opcode := opcode
  alu_ctrl.io.funct3 := funct3
  alu_ctrl.io.funct7 := funct7

  // lab1(Execute)

  //补充部分很明显就是补做什么运算、操作数 1、操作数 2

  alu.io.func := alu_ctrl.io.alu_funct//ALUControl 的输出 → ALU 的输入

  alu.io.op1 := Mux(//根据 aluop1_source 选择 ALU 的第一个操作数
    io.aluop1_source === ALUOp1Source.InstructionAddress,
    io.instruction_address,
    io.reg1_data
  )
  alu.io.op2 := Mux(io.aluop2_source === ALUOp2Source.Immediate,//根据 aluop2_source 选择 ALU 的第二个操作数
    io.immediate,
    io.reg2_data
  )

  // lab1(Execute) end

  io.mem_alu_result := alu.io.result//ALU 的输出 → Execute 的输出

  /*
  是否跳转 =
    是 jal
    或者是 jalr
    或者（是条件分支，并且分支条件成立）
  */
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

/*
| 分支指令 | 跳转条件 |
| `beq` | 两个值相等 |
| `bne` | 两个值不等 |
| `blt` | 第一个值小于第二个，按有符号解释 |
| `bge` | 第一个值大于等于第二个，按有符号解释 |
| `bltu` | 第一个值小于第二个，按无符号解释 |
| `bgeu` | 第一个值大于等于第二个，按无符号解释 |
*/      
      )
    )
  io.if_jump_address := io.immediate + Mux(opcode === Instructions.jalr, io.reg1_data, io.instruction_address)//jalr 指令的跳转地址 = 寄存器1的值 + 立即数；其他跳转指令的跳转地址 = 当前指令地址 + 立即数
}
