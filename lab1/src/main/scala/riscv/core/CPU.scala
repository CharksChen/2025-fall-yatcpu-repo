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

package riscv.core

import chisel3._
import chisel3.util.Cat
import riscv.{CPUBundle, Parameters}

class CPU extends Module {
  val io = IO(new CPUBundle)

  val regs = Module(new RegisterFile)//  寄存器堆模块,	保存、读取通用寄存器
  val inst_fetch = Module(new InstructionFetch)//  指令获取模块,	从内存中获取指令
  val id = Module(new InstructionDecode)//  指令译码模块,	解析指令，生成控制信号
  val ex = Module(new Execute)//  执行模块,	根据控制信号执行运算
  val mem = Module(new MemoryAccess)//  访存模块,	根据控制信号访问内存
  val wb = Module(new WriteBack)//  写回模块,	将结果写回寄存器堆


  io.deviceSelect := mem.io.memory_bundle.address(Parameters.AddrBits - 1, Parameters.AddrBits - Parameters.SlaveDeviceCountBits)

  inst_fetch.io.jump_address_id := ex.io.if_jump_address//执行模块计算“是否跳转、跳到哪里”，取指模块接收这些信号，决定 PC 下一拍的值
  inst_fetch.io.jump_flag_id := ex.io.if_jump_flag
  inst_fetch.io.instruction_valid := io.instruction_valid// 取指模块接收外部内存的指令有效信号，决定是否更新 PC
  inst_fetch.io.instruction_read_data := io.instruction// 取指模块接收外部内存的指令数据，供译码模块使用
  io.instruction_address := inst_fetch.io.instruction_address//  CPU 模块的输出端口，输出当前机器指令的地址,即 PC

  regs.io.write_enable := id.io.reg_write_enable//  寄存器堆模块接收译码模块的寄存器写使能信号，决定是否写入寄存器
  regs.io.write_address := id.io.reg_write_address//  寄存器堆模块接收译码模块的寄存器写地址，决定写入哪个寄存器
  regs.io.write_data := wb.io.regs_write_data//  寄存器堆模块接收写回模块的数据，用于写入寄存器
  regs.io.read_address1 := id.io.regs_reg1_read_address//  寄存器堆模块接收译码模块的寄存器1读地址，决定读取哪个寄存器
  regs.io.read_address2 := id.io.regs_reg2_read_address//  寄存器堆模块接收译码模块的寄存器2读地址，决定读取哪个寄存器

  regs.io.debug_read_address := io.debug_read_address//  寄存器堆模块接收外部调试的寄存器读地址，决定读取哪个寄存器
  io.debug_read_data := regs.io.debug_read_data//  CPU 模块的输出端口，输出寄存器堆模块的调试读数据

  id.io.instruction := inst_fetch.io.instruction//  译码模块接收取指模块的当前机器指令，解析指令，生成控制信号

  // lab1(cpu)
   
 /*
| 执行模块输入 | 需要什么 | 谁能提供 |
| `instruction` | 当前指令，供 ALU 控制和分支判断使用 | 取指模块 |
| `instruction_address` | 当前 PC，供地址计算使用 | 取指模块 |
| `reg1_data` | 第一个源寄存器中的值 | 寄存器堆 |
| `reg2_data` | 第二个源寄存器中的值 | 寄存器堆 |
| `immediate` | 提取并扩展后的立即数 | 译码模块 |
| `aluop1_source` | ALU 第一个输入选什么 | 译码模块 |
| `aluop2_source` | ALU 第二个输入选什么 | 译码模块 |
 */  
  ex.io.instruction         := inst_fetch.io.instruction//  执行模块接收取指模块的当前机器指令，供 ALU 控制和分支判断使用
  ex.io.instruction_address := inst_fetch.io.instruction_address//  执行模块接收取指模块的当前 PC，供地址计算使用
  ex.io.reg1_data           := regs.io.read_data1//  执行模块接收寄存器堆的第一个源寄存器中的值
  ex.io.reg2_data           := regs.io.read_data2//  执行模块接收寄存器堆的第二个源寄存器中的值
  ex.io.immediate           := id.io.ex_immediate//  执行模块接收译码模块提取并扩展后的立即数
  ex.io.aluop1_source       := id.io.ex_aluop1_source//  执行模块接收译码模块的 ALU 第一个输入选什么
  ex.io.aluop2_source       := id.io.ex_aluop2_source//  执行模块接收译码模块的 ALU 第二个输入选什么
  

  // lab1(cpu) end


  mem.io.alu_result := ex.io.mem_alu_result
  mem.io.reg2_data := regs.io.read_data2
  mem.io.memory_read_enable := id.io.memory_read_enable
  mem.io.memory_write_enable := id.io.memory_write_enable
  mem.io.funct3 := inst_fetch.io.instruction(14, 12)
/*
| 访存需要的信息 | 来源 |
| 访问地址 | 执行模块计算结果 |
| 待写入的数据 | 第二个源寄存器的数据 |
| 是否读、是否写 | 译码控制信号 |
| 读写多少字节、如何扩展 | 指令中的 `funct3` |

*/


  io.memory_bundle.address := Cat(0.U(Parameters.SlaveDeviceCountBits.W),mem.io.memory_bundle.address(Parameters.AddrBits - 1 - Parameters.SlaveDeviceCountBits, 0))//  将访存模块的地址传递给外部内存模块，补充高位的设备选择位
  io.memory_bundle.write_enable := mem.io.memory_bundle.write_enable//  将访存模块的写使能信号传递给外部内存模块
  io.memory_bundle.write_data := mem.io.memory_bundle.write_data//  将访存模块的写数据传递给外部内存模块
  io.memory_bundle.write_strobe := mem.io.memory_bundle.write_strobe//  将访存模块的写字节使能信号传递给外部内存模块
  mem.io.memory_bundle.read_data := io.memory_bundle.read_data//  将外部内存模块的读数据传递给访存模块

  wb.io.instruction_address := inst_fetch.io.instruction_address//  写回模块接收取指模块的当前机器指令地址,即 PC
  wb.io.alu_result := ex.io.mem_alu_result//  写回模块接收执行模块的 ALU 运算结果
  wb.io.memory_read_data := mem.io.wb_memory_read_data//  写回模块接收访存模块的从内存读取的数据
  wb.io.regs_write_source := id.io.wb_reg_write_source//  写回模块接收译码模块的寄存器写回数据来源选择信号，决定写回数据的来源
}
