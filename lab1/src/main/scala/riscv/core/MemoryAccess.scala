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

package riscv.core

import chisel3._
import chisel3.util._
import peripheral.RAMBundle
import riscv.Parameters

class MemoryAccess extends Module {
  val io = IO(new Bundle() {
    val alu_result = Input(UInt(Parameters.DataWidth))//	ALU 算出的访存地址
    val reg2_data = Input(UInt(Parameters.DataWidth))//store 指令需要写入内存的数据
    val memory_read_enable = Input(Bool())//使能信号，用于控制是否进行内存读取操作
    val memory_write_enable = Input(Bool())//使能信号，用于控制是否进行内存写入操作
    val funct3 = Input(UInt(3.W))//funct3 字段，用于确定内存访问的类型

    val wb_memory_read_data = Output(UInt(Parameters.DataWidth))//	从内存读取的数据，供写回模块使用

    val memory_bundle = Flipped(new RAMBundle)//  与内存模块的接口,与外部内存交换地址、数据和写入控制
  })
  val mem_address_index = io.alu_result(log2Up(Parameters.WordSize) - 1, 0).asUInt// 这行代码是在取“字内偏移”,计算内存地址的索引，用于确定要访问的字节或半字,字内偏移 = 地址 % 4

  io.memory_bundle.write_enable := false.B//  默认不写入内存
  io.memory_bundle.write_data := 0.U//  默认写入数据为 0
  io.memory_bundle.address := io.alu_result//  将 ALU 算出的访存地址传递给内存模块
  io.memory_bundle.write_strobe := VecInit(Seq.fill(Parameters.WordSize)(false.B))//  默认不写入内存的任何字节
  io.wb_memory_read_data := 0.U//  默认从内存读取的数据为 0

  when(io.memory_read_enable) {
    val data = io.memory_bundle.read_data
    io.wb_memory_read_data := MuxLookup(//根据 funct3 字段选择不同的内存访问类型
      io.funct3,
      0.U,
      IndexedSeq(
        InstructionsTypeL.lb -> MuxLookup(//根据字内偏移选择要读取的字节，并进行符号扩展
          mem_address_index,
          Cat(Fill(24, data(31)), data(31, 24)),
          IndexedSeq(
            0.U -> Cat(Fill(24, data(7)), data(7, 0)),
            1.U -> Cat(Fill(24, data(15)), data(15, 8)),
            2.U -> Cat(Fill(24, data(23)), data(23, 16))
          )
        ),
        InstructionsTypeL.lbu -> MuxLookup(//根据字内偏移选择要读取的字节，并进行零扩展
          mem_address_index,
          Cat(Fill(24, 0.U), data(31, 24)),
          IndexedSeq(
            0.U -> Cat(Fill(24, 0.U), data(7, 0)),
            1.U -> Cat(Fill(24, 0.U), data(15, 8)),
            2.U -> Cat(Fill(24, 0.U), data(23, 16))
          )
        ),
        InstructionsTypeL.lh -> Mux(//根据字内偏移选择要读取的半字，并进行符号扩展
          mem_address_index === 0.U,
          Cat(Fill(16, data(15)), data(15, 0)),
          Cat(Fill(16, data(31)), data(31, 16))

        ),
        InstructionsTypeL.lhu -> Mux(//根据字内偏移选择要读取的半字，并进行零扩展
          mem_address_index === 0.U,
          Cat(Fill(16, 0.U), data(15, 0)),
          Cat(Fill(16, 0.U), data(31, 16))
        ),
        InstructionsTypeL.lw -> data//直接读取整个字
      )
    )
  }.elsewhen(io.memory_write_enable) {
    io.memory_bundle.write_data := io.reg2_data
    io.memory_bundle.write_enable := true.B
    io.memory_bundle.write_strobe := VecInit(Seq.fill(Parameters.WordSize)(false.B))
    when(io.funct3 === InstructionsTypeS.sb) {
      io.memory_bundle.write_strobe(mem_address_index) := true.B
      io.memory_bundle.write_data := io.reg2_data(Parameters.ByteBits, 0) << (mem_address_index << log2Up(Parameters.ByteBits).U)
    }.elsewhen(io.funct3 === InstructionsTypeS.sh) {
      when(mem_address_index === 0.U) {
        for (i <- 0 until Parameters.WordSize / 2) {
          io.memory_bundle.write_strobe(i) := true.B
        }
        io.memory_bundle.write_data := io.reg2_data(Parameters.WordSize / 2 * Parameters.ByteBits, 0)
      }.otherwise {
        for (i <- Parameters.WordSize / 2 until Parameters.WordSize) {
          io.memory_bundle.write_strobe(i) := true.B
        }
        io.memory_bundle.write_data := io.reg2_data(Parameters.WordSize / 2 * Parameters.ByteBits, 0) << (Parameters
          .WordSize / 2 * Parameters.ByteBits)
      }
    }.elsewhen(io.funct3 === InstructionsTypeS.sw) {
      for (i <- 0 until Parameters.WordSize) {
        io.memory_bundle.write_strobe(i) := true.B
      }
    }
  }
}
/*
| 操作 | 数据来源 | 字节写掩码 `[3:0]` |
| `sb` | 寄存器低 8 位，移到目标字节 | 根据偏移打开一位 |
| `sh`，偏移 0 | 寄存器低 16 位 | `0011` |
| `sh`，偏移 2 | 寄存器低 16 位左移 16 位 | `1100` |
| `sw` | 寄存器完整 32 位 | `1111` |
*/
