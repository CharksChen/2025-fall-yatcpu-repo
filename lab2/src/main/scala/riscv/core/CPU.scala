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
单周期CPU顶层连线图的代码化表达：IF/ID/EX/MEM/WB沿用Lab1；CSR保存状态，CLINT根据请求决定陷入/返回PC。重点跟踪CSR旧值与新值分别流向何处。
*/
package riscv.core

import chisel3._
import chisel3.util.Cat
import riscv.{CPUBundle, Parameters}

class CPU extends Module {
  val io = IO(new CPUBundle)

  // 普通寄存器堆x0..x31，保存程序计算结果；与CSR独立。
  val regs = Module(new RegisterFile)
  val inst_fetch = Module(new InstructionFetch)
  val id = Module(new InstructionDecode)
  val ex = Module(new Execute)
  val mem = Module(new MemoryAccess)
  val wb = Module(new WriteBack)
  // 新增控制状态寄存器组；CLINT是中断决策模块。
  val csr_regs = Module(new CSR)
  val clint = Module(new CLINT)

  io.regs_debug_read_data := regs.io.debug_read_data
  io.csr_regs_debug_read_data := csr_regs.io.debug_reg_read_data
  regs.io.debug_read_address := io.regs_debug_read_address
  csr_regs.io.debug_reg_read_address := io.csr_regs_debug_read_address

  // 根据访存地址高位选择哪个外设，便于之后实现Timer的MMIO。
  io.deviceSelect := mem.io.memory_bundle.address(Parameters.AddrBits - 1, Parameters.AddrBits - Parameters.SlaveDeviceCountBits)

  // 普通跳转来自EX；CLINT的陷入/返回跳转在IF中优先级更高。
  inst_fetch.io.jump_address_id := ex.io.if_jump_address
  inst_fetch.io.jump_flag_id := ex.io.if_jump_flag
  inst_fetch.io.interrupt_assert := clint.io.interrupt_assert
  inst_fetch.io.interrupt_handler_address := clint.io.interrupt_handler_address
  inst_fetch.io.instruction_valid := io.instruction_valid
  inst_fetch.io.instruction_read_data := io.instruction
  io.instruction_address := inst_fetch.io.instruction_address

  // 通用寄存器写回：ID决定写不写/写到哪里；WB选择实际写入的数据。
  regs.io.write_enable := id.io.reg_write_enable
  regs.io.write_address := id.io.reg_write_address
  regs.io.write_data := wb.io.regs_write_data
  regs.io.read_address1 := id.io.regs_reg1_read_address
  regs.io.read_address2 := id.io.regs_reg2_read_address


  // 当前指令同时用于ID生成控制、EX计算结果和CLINT判断特殊环境指令。
  id.io.instruction := inst_fetch.io.instruction

  // <>连接一整个双向接口：CLINT能读取CSR状态，并通过高优先级通路更新关键CSR。
  csr_regs.io.clint_access_bundle <> clint.io.csr_bundle
  csr_regs.io.reg_read_address_id := id.io.csr_reg_address
  // CSR指令的普通写通路：ID给地址/写使能，EX给新值。
  csr_regs.io.reg_write_data_ex := ex.io.csr_reg_write_data
  csr_regs.io.reg_write_address_id := id.io.csr_reg_address
  csr_regs.io.reg_write_enable_id := id.io.csr_reg_write_enable

  ex.io.instruction := inst_fetch.io.instruction
  ex.io.instruction_address := inst_fetch.io.instruction_address
  ex.io.reg1_data := regs.io.read_data1
  ex.io.reg2_data := regs.io.read_data2
  ex.io.immediate := id.io.ex_immediate
  ex.io.aluop1_source := id.io.ex_aluop1_source
  ex.io.aluop2_source := id.io.ex_aluop2_source
  // 旧CSR值被EX用于计算新值，同时被WB用于写回rd。
  ex.io.csr_reg_read_data := csr_regs.io.reg_read_data

  mem.io.alu_result := ex.io.mem_alu_result
  mem.io.reg2_data := regs.io.read_data2
  mem.io.memory_read_enable := id.io.memory_read_enable
  mem.io.memory_write_enable := id.io.memory_write_enable
  mem.io.funct3 := inst_fetch.io.instruction(14, 12)

  io.memory_bundle.address := Cat(0.U(Parameters.SlaveDeviceCountBits.W),mem.io.memory_bundle.address(Parameters.AddrBits - 1 - Parameters.SlaveDeviceCountBits, 0))
  io.memory_bundle.write_enable := mem.io.memory_bundle.write_enable
  io.memory_bundle.write_data := mem.io.memory_bundle.write_data
  io.memory_bundle.write_strobe := mem.io.memory_bundle.write_strobe
  mem.io.memory_bundle.read_data := io.memory_bundle.read_data

  wb.io.instruction_address := inst_fetch.io.instruction_address
  wb.io.alu_result := ex.io.mem_alu_result
  wb.io.memory_read_data := mem.io.wb_memory_read_data
  wb.io.regs_write_source := id.io.wb_reg_write_source
  wb.io.csr_read_data := csr_regs.io.reg_read_data

  // CLINT读取指令/PC/跳转信息/外设请求，并生成interrupt_assert。
  clint.io.instruction := inst_fetch.io.instruction
  clint.io.instruction_address := inst_fetch.io.instruction_address
  clint.io.interrupt_flag := io.interrupt_flag
  clint.io.jump_flag := ex.io.if_jump_flag
  clint.io.jump_address := ex.io.if_jump_address

}
