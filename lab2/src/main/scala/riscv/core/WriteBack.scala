// Copyright 2022 Canbin Huang
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
Lab2新增CSR写回来源。两条路径一定区分：旧CSR值→通用rd；新CSR值→CSR寄存器组。这里处理前者。
*/
package riscv.core

import chisel3._
import chisel3.util._
import riscv.Parameters

class WriteBack extends Module {
  val io = IO(new Bundle() {
    val instruction_address = Input(UInt(Parameters.AddrWidth))
    val alu_result = Input(UInt(Parameters.DataWidth))
    val memory_read_data = Input(UInt(Parameters.DataWidth))
    val regs_write_source = Input(UInt(2.W))
    val csr_read_data = Input(UInt(Parameters.DataWidth))
    val regs_write_data = Output(UInt(Parameters.DataWidth))
  })
  // 根据ID控制信号选择ALU结果、访存结果、CSR旧值或PC+4写回rd。
  io.regs_write_data := MuxLookup(
    io.regs_write_source,
    io.alu_result,
    IndexedSeq(
      RegWriteSource.Memory -> io.memory_read_data,
      RegWriteSource.CSR -> io.csr_read_data,
      RegWriteSource.NextInstructionAddress -> (io.instruction_address + 4.U),
    )
  )
}
