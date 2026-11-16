// Copyright 2022 hrpccs
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
TimerTest检查MMIO写limit和写enabled；尚未充分覆盖计数器到期后的中断信号波形。
*/
package riscv.singlecycle

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import riscv.{Parameters, TestAnnotations}
import peripheral.{RAMBundle, Timer}

class TimerTest extends AnyFlatSpec with ChiselScalatestTester {

  // 包装Timer使测试直接访问RAMBundle和调试端口。
  class TestTimer extends Module {
    val io = IO(new Bundle {
      val debug_limit = Output(UInt(Parameters.DataWidth))
      val debug_enabled = Output(Bool())
      val bundle = new RAMBundle

      val write_strobe = Input(UInt(4.W))
    })
    val timer = Module(new Timer)
    io.debug_limit := timer.io.debug_limit
    io.debug_enabled := timer.io.debug_enabled

    timer.io.bundle <> io.bundle
    timer.io.bundle.write_strobe := VecInit(io.write_strobe.asBools)
  }

  behavior of "Timer"
  it should "read and write the limit" in {
    test(new TestTimer).withAnnotations(TestAnnotations.annos) {c =>
      // 四字节写掩码1111，允许写完整32位字。
      c.io.write_strobe.poke(0xF.U)
      c.io.bundle.write_enable.poke(true.B)
      // 向内部偏移0x4写limit，对应完整映射0x80000004。
      c.io.bundle.address.poke(0x4.U)
      c.io.bundle.write_data.poke(0x990315.U)
      c.clock.step()
      c.io.bundle.write_enable.poke(false.B)
      c.clock.step()
      c.io.debug_limit.expect(0x990315.U)
      c.io.bundle.write_enable.poke(true.B)
      // 向内部偏移0x8写0关闭计时器，对应0x80000008。
      c.io.bundle.address.poke(0x8.U)
      c.io.bundle.write_data.poke(0.U)
      c.clock.step()
      c.io.bundle.write_enable.poke(false.B)
      c.io.debug_enabled.expect(false.B)
    }
  }
}

