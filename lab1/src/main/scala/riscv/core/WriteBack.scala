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
import riscv.Parameters

class WriteBack extends Module {
  val io = IO(new Bundle() {
    val instruction_address = Input(UInt(Parameters.AddrWidth))//当前机器指令的地址,即PC
    val alu_result = Input(UInt(Parameters.DataWidth))//ALU运算结果，供写回模块使用
    val memory_read_data = Input(UInt(Parameters.DataWidth))//从内存读取的数据，供写回模块使用
    val regs_write_source = Input(UInt(2.W))//寄存器写回数据来源选择信号，0表示来自ALU运算结果，1表示来自内存读取数据，2表示来自下一条指令地址
    val regs_write_data = Output(UInt(Parameters.DataWidth))//寄存器写回数据，供寄存器堆使用
  })


/*
选择信号为 1：输出内存读取数据
选择信号为 3：输出当前 PC + 4
其他编码：输出 ALU 结果
*/  
  io.regs_write_data := MuxLookup(
    io.regs_write_source,//根据寄存器写回数据来源选择信号选择不同的数据来源
    io.alu_result,//默认输出 ALU 运算结果
    IndexedSeq(
      RegWriteSource.Memory -> io.memory_read_data,//选择信号为 1：输出内存读取数据
      RegWriteSource.NextInstructionAddress -> (io.instruction_address + 4.U)//选择信号为 3：输出当前 PC + 4
    )
  )
}
