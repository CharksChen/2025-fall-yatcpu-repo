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
CSR模块是独立编址的控制状态寄存器组，不能与普通x0..x31混淆。顺序：CSR读旧值→EX算新值→CLINT读取本拍有效状态→高优先级直写或普通写入。
*/
package riscv.core

import chisel3._
import chisel3.util._
import riscv.Parameters


// CSR用12位地址寻址，0x300=mstatus、0x305=mtvec、0x341=mepc。
object CSRRegister {
  // Refer to Spec. Vol.II Page 8-10
  val MSTATUS = 0x300.U(Parameters.CSRRegisterAddrWidth)
  val MIE = 0x304.U(Parameters.CSRRegisterAddrWidth)
  val MTVEC = 0x305.U(Parameters.CSRRegisterAddrWidth)
  val MSCRATCH = 0x340.U(Parameters.CSRRegisterAddrWidth)
  val MEPC = 0x341.U(Parameters.CSRRegisterAddrWidth)
  val MCAUSE = 0x342.U(Parameters.CSRRegisterAddrWidth)
  val CycleL = 0xc00.U(Parameters.CSRRegisterAddrWidth)
  val CycleH = 0xc80.U(Parameters.CSRRegisterAddrWidth)
}

class CSR extends Module {
  val io = IO(new Bundle {
    val reg_read_address_id = Input(UInt(Parameters.CSRRegisterAddrWidth))
    val reg_write_enable_id= Input(Bool())
    val reg_write_address_id = Input(UInt(Parameters.CSRRegisterAddrWidth))
    val reg_write_data_ex= Input(UInt(Parameters.DataWidth))
    val debug_reg_read_address = Input(UInt(Parameters.CSRRegisterAddrWidth))

    val debug_reg_read_data = Output(UInt(Parameters.DataWidth))
    val reg_read_data = Output(UInt(Parameters.DataWidth))

    val clint_access_bundle = Flipped(new CSRDirectAccessBundle)
  })

  // mstatus bit3=MIE全局中断使能，bit7=MPIE保存进入trap前的使能状态。
  val mstatus = RegInit(UInt(Parameters.DataWidth), 0.U)
  val mie = RegInit(UInt(Parameters.DataWidth), 0.U)
  val mtvec = RegInit(UInt(Parameters.DataWidth), 0.U)
  val mscratch = RegInit(UInt(Parameters.DataWidth), 0.U)
  val mepc = RegInit(UInt(Parameters.DataWidth), 0.U)
  val mcause = RegInit(UInt(Parameters.DataWidth), 0.U)
  // cycles是64位递增周期计数器，提供低32位与高32位两个只读观察窗口。
  val cycles = RegInit(UInt(64.W), 0.U)
  // 查找表把CSR地址映射为当前寄存器内容；MuxLookup实现组合读取。
  val regLUT =
    IndexedSeq(
      CSRRegister.MSTATUS -> mstatus,
      CSRRegister.MIE -> mie,
      CSRRegister.MTVEC -> mtvec,
      CSRRegister.MSCRATCH -> mscratch,
      CSRRegister.MEPC -> mepc,
      CSRRegister.MCAUSE -> mcause,
      CSRRegister.CycleL -> cycles(31, 0),
      CSRRegister.CycleH -> cycles(63, 32),
    )
  cycles := cycles + 1.U

  // If the pipeline and the CLINT are going to read and write the CSR at the same time, let the pipeline write first.
  // This is implemented in a single cycle by passing reg_write_data_ex to clint and writing the data from the CLINT to the CSR.
  // 普通读口供EX和WB获取旧值；独立debug口用于测试。
  io.reg_read_data := MuxLookup(io.reg_read_address_id, 0.U, regLUT)
  io.debug_reg_read_data := MuxLookup(io.debug_reg_read_address, 0.U,regLUT)

  // 实验留白：CLINT应看到CPU本拍将生效的CSR状态。特别是CSR指令同拍清除MIE时，不能再按旧状态响应中断。注意组合前递与避免反馈环。
  //lab2(CLINTCSR)
  //what data should be passed from csr to clint (Note: what should clint see is the next state of the CPU)
  /*
  io.clint_access_bundle.mstatus :=
  io.clint_access_bundle.mtvec :=
  io.clint_access_bundle.mcause :=
  io.clint_access_bundle.mepc :=
  */

  // CLINT直写优先级高，可在同一周期同时修改mstatus/mepc/mcause以完成陷入或返回。
  when(io.clint_access_bundle.direct_write_enable) {
    mstatus := io.clint_access_bundle.mstatus_write_data
    mepc := io.clint_access_bundle.mepc_write_data
    mcause := io.clint_access_bundle.mcause_write_data
  // 仅在CLINT不直写时，普通CSR指令才能提交对三个中断状态寄存器的写入。
  }.elsewhen(io.reg_write_enable_id) {
    when(io.reg_write_address_id === CSRRegister.MSTATUS) {
      mstatus := io.reg_write_data_ex
    }.elsewhen(io.reg_write_address_id === CSRRegister.MEPC) {
      mepc := io.reg_write_data_ex
    }.elsewhen(io.reg_write_address_id === CSRRegister.MCAUSE) {
      mcause := io.reg_write_data_ex
    }
  }

  // mie/mtvec/mscratch另走普通写入通路，不属于CLINT同时直写的三个寄存器。
  when(io.reg_write_enable_id) {
    when(io.reg_write_address_id === CSRRegister.MIE) {
      mie := io.reg_write_data_ex
    }.elsewhen(io.reg_write_address_id === CSRRegister.MTVEC){
      mtvec := io.reg_write_data_ex
    }.elsewhen(io.reg_write_address_id === CSRRegister.MSCRATCH) {
      mscratch := io.reg_write_data_ex
    }
  }


}
