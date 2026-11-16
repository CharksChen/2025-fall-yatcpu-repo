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
poke给输入赋值，expect断言组合输出，clock.step前进一拍。该测试仅检查CSR新值计算，不涉及CSR寄存器真正写入。
*/
package riscv.singlecycle

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import riscv.TestAnnotations
import riscv.core.Execute

class ExecuteTest extends AnyFlatSpec with ChiselScalatestTester{
  behavior of "CLINTCSRTest of Single Cycle CPU"
  it should "produce correct data for csr write" in {
    test(new Execute).withAnnotations(TestAnnotations.annos) { c =>
      // 机器码实际对应CSRRCI zimm=8：0x1888 & ~8=0x1880，不要误用reg1_data。
      c.io.instruction.poke(0x30047073L.U) // csrc mstatus,3
      c.io.csr_reg_read_data.poke(0x1888L.U)
      c.io.reg1_data.poke(0x1880L.U)
      c.io.csr_reg_write_data.expect(0x1880.U)
      c.clock.step()
      // CSRRSI zimm=8：0x1880 | 8 = 0x1888。
      c.io.instruction.poke(0x30046073L.U) //csrs mastatus,3
      c.io.csr_reg_read_data.poke(0x1880L.U)
      c.io.reg1_data.poke(0x1880L.U)
      c.io.csr_reg_write_data.expect(0x1888.U)
      c.clock.step()
      // CSRRW：直接将rs1中的0x1888写为CSR新值。
      c.io.instruction.poke(0x30051073L.U) //csrw mstatus, a0
      c.io.csr_reg_read_data.poke(0.U)
      c.io.reg1_data.poke(0x1888L.U)
      c.io.csr_reg_write_data.expect(0x1888.U)
      c.clock.step()
      // CSRRS rs1=x0：CSR新值公式不改变旧值。
      c.io.instruction.poke(0x30002573L.U) //csrr a0, mstatus
      c.io.csr_reg_read_data.poke(0x1888.U)
      c.io.reg1_data.poke(0x0L.U)
      c.io.csr_reg_write_data.expect(0x1888.U)
      c.clock.step()
    }
  }
}
