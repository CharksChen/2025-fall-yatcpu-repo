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
IF维护PC。若instruction_valid有效：中断跳转优先级最高，其次普通jal/branch，最后PC+4；PC是RegInit寄存器，时钟边沿更新。
*/
package riscv.core

import chisel3._
import riscv.Parameters

object ProgramCounter {
  val EntryAddress = Parameters.EntryAddress
}

class InstructionFetch extends Module {
  val io = IO(new Bundle {
    val jump_flag_id = Input(Bool())
    val jump_address_id = Input(UInt(Parameters.AddrWidth))
    val instruction_read_data = Input(UInt(Parameters.DataWidth))
    val interrupt_assert = Input(Bool())
    val interrupt_handler_address = Input(UInt(Parameters.AddrWidth))
    val instruction_valid = Input(Bool())

    val instruction_address = Output(UInt(Parameters.AddrWidth))
    val instruction = Output(UInt(Parameters.InstructionWidth))

  })
  // 复位PC为程序入口0x1000；寄存器值在时钟沿更新。
  val pc = RegInit(ProgramCounter.EntryAddress)

  // 只有指令有效才推进PC；指令ROM未准备好时保持原PC。
  when(io.instruction_valid) {
    // CLINT要求trap或mret跳转时覆盖普通PC选择。
    when(io.interrupt_assert){
      pc := io.interrupt_handler_address
    // 未发生trap时才采用EX提供的普通分支/跳转地址。
    }.elsewhen(io.jump_flag_id){
      pc := io.jump_address_id
    }.otherwise {
      pc := pc + 4.U
    }
    // 本拍用于执行的指令来自外部指令存储器，不能将下一拍PC与当前指令混淆。
    io.instruction := io.instruction_read_data
  }.otherwise{
    pc := pc
    io.instruction := 0x00000013.U
  }
  io.instruction_address := pc
}
