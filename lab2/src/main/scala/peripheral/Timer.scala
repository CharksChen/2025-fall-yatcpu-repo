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
MMIO定时器：count持续计数、limit决定时间门限、enabled允许或禁止发出signal_interrupt。完整地址0x80000004/08分别映射limit/enable，模块内部接收偏移0x4/0x8。
*/
package peripheral

import chisel3._
import chisel3.util._
import riscv.Parameters

class Timer extends Module {
  val io = IO(new Bundle {
    // 把Timer当作可寻址设备，复用RAM的address/write_data/write_enable/write_strobe/read_data。
    val bundle = new RAMBundle
    // 送给CLINT的中断请求，不直接操作PC。
    val signal_interrupt = Output(Bool())

    // 测试专用的limit观察口，和普通MMIO读口不同。
    val debug_limit = Output(UInt(Parameters.DataWidth))
    val debug_enabled = Output(Bool())
  })

  // 三个寄存器：count计数、limit阈值、enabled是否允许中断。
  val count = RegInit(0.U(32.W))
  val limit = RegInit(100000000.U(32.W))
  io.debug_limit := limit
  val enabled = RegInit(true.B)
  io.debug_enabled := enabled

  // 实验留白：补全MMIO读写、按strobe写寄存器、计数器更新与中断脉冲。TimerTest验证0x4写limit和0x8写0关闭定时器。
  //lab2(CLINTCSR)
  //finish the read-write for count,limit,enabled. And produce appropriate signal_interrupt
}
