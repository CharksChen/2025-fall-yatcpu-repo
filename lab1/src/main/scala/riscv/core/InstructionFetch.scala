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
import riscv.Parameters

object ProgramCounter {
  val EntryAddress = Parameters.EntryAddress
}



class InstructionFetch extends Module {//创建一个名为 InstructionFetch 的硬件模块        
  val io = IO(new Bundle {//创建一个名为 io 的接口，类型为 Bundle
    val jump_flag_id = Input(Bool())  //创建一个名为 jump_flag_id 的输入端口，类型为 Bool
    val jump_address_id = Input(UInt(Parameters.AddrWidth))//创建一个名为 jump_address_id 的输入端口，类型为 UInt，宽度为 Parameters.AddrWidth
    val instruction_read_data = Input(UInt(Parameters.DataWidth))//创建一个名为 instruction_read_data 的输入端口，类型为 UInt，宽度为 Parameters.DataWidth
    val instruction_valid = Input(Bool())//创建一个名为 instruction_valid 的输入端口，类型为 Bool

    val instruction_address = Output(UInt(Parameters.AddrWidth))//创建一个名为 instruction_address 的输出端口，类型为 UInt，宽度为 Parameters.AddrWidth
    val instruction = Output(UInt(Parameters.InstructionWidth))//创建一个名为 instruction 的输出端口，类型为 UInt，宽度为 Parameters.InstructionWidth
  })
  val pc = RegInit(ProgramCounter.EntryAddress)     //创建一个寄存器pc，并指定复位值为程序入口地址
  /*  val pc：给这个硬件对象取名为 pc      
          ！注意和var做区分，val 不能重新赋值，var 可以重新赋值
          val 不意味着寄存器里的数值不能变化。 它表示 Scala 中的这个名字绑定不变；所描述的硬件寄存器仍然可以更新。
      RegInit(...)：创建一个寄存器，并指定复位值。
      ProgramCounter.EntryAddress：程序入口地址；当前项目配置为 0x1000

  */

  when(io.instruction_valid) {
    io.instruction := io.instruction_read_data
    // lab1(InstructionFetch)
    when(io.jump_flag_id) {
     pc := io.jump_address_id
     }.otherwise {
     pc := pc + 4.U

     }  
    //空白处只需决定指令有效时下一个pc是多少,因为无效时保持原来的pc不变，只用关注变化就行

    /*如果要求跳转：
          下一拍 PC = 跳转地址
      否则：
          下一拍 PC = 当前 PC + 4   
          
   */

    // la1(InstructionFetch) end


  }.otherwise{
    pc := pc       //指令无效时保持pc
    io.instruction := 0x00000013.U    //将输出的指令设为 0x00000013，这是 RISC-V 中常用的空操作指令 NOP 的机器码。
  }
  io.instruction_address := pc    //将pc的值作为取值地址输出到io.instruction_address
}
