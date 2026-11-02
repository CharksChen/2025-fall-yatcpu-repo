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
      0x002081B3
                0000 0000 0010 0000 1000 0001 1011 0011 

        R型指令  0000000 00010 00001 000 00011 0110011
                 opcode   rd  funct3 rs1  rs2  funct7

     切开时  val rd = io.instruction(11, 7)取11~7位          


*/
package riscv.core

import chisel3._
import chisel3.util._
import riscv.Parameters

object InstructionTypes {  //不同指令类别的opcode
  val L = "b0000011".U     //这里的 "b..." 表示二进制常量，`.U` 将其表示为 Chisel 的无符号硬件常量。
  val I = "b0010011".U
  val S = "b0100011".U
  val RM = "b0110011".U
  val B = "b1100011".U
}//L对应加载数据，I对应立即数运算，S对应存储数据，RM对应寄存器运算，B对应分支跳转，后续会用到

object Instructions {   //同样是给opcode赋值，区别是这里是具体的指令
  val lui = "b0110111".U
  val nop = "b0000001".U
  val jal = "b1101111".U
  val jalr = "b1100111".U
  val auipc = "b0010111".U
  val csr = "b1110011".U
  val fence = "b0001111".U
}//lui对应加载上半部分立即数，nop对应空操作，jal对应跳转并链接，jalr对应寄存器间接跳转并链接，auipc对应加载上半部分立即数到PC，csr对应控制状态寄存器操作，fence对应内存屏障
/*这里有一个容易误导的名称：Instructions.nop 的值不能当作标准 NOP 的 opcode。
  它是一个自定义的指令，opcode 为 0x00000013，功能是将寄存器 x0 写入 0。
  在 RISC-V 中，标准 NOP 的 opcode 是 0x00000013，但它的 funct3 和 funct7 都是 0。
  所以，Instructions.nop 并不是标准 NOP，而是一个自定义的指令。
*/
object InstructionsTypeL {//加载指令的 funct3,同一个加载类 opcode 下，通过 funct3 区分读取多少位、如何扩展：
  val lb = "b000".U
  val lh = "b001".U
  val lw = "b010".U
  val lbu = "b100".U
  val lhu = "b101".U
}//lb对应加载字节，lh对应加载半字，lw对应加载字，lbu对应加载无符号字节，lhu对应加载无符号半字

object InstructionsTypeI {
  val addi = 0.U
  val slli = 1.U
  val slti = 2.U
  val sltiu = 3.U
  val xori = 4.U
  val sri = 5.U
  val ori = 6.U
  val andi = 7.U
}//addi对应立即数加法，slli对应立即数左移，slti对应立即数小于，sltiu对应立即数无符号小于，xori对应立即数异或，sri对应立即数右移，ori对应立即数或，andi对应立即数与

object InstructionsTypeS {
  val sb = "b000".U
  val sh = "b001".U
  val sw = "b010".U
}//sb对应存储字节，sh对应存储半字，sw对应存储字

object InstructionsTypeR {
  val add_sub = 0.U
  val sll = 1.U
  val slt = 2.U
  val sltu = 3.U
  val xor = 4.U
  val sr = 5.U
  val or = 6.U
  val and = 7.U
}//add_sub对应加减法，sll对应左移，slt对应小于，sltu对应无符号小于，xor对应异或，sr对应右移，or对应或，and对应与

object InstructionsTypeM {
  val mul = 0.U
  val mulh = 1.U
  val mulhsu = 2.U
  val mulhum = 3.U
  val div = 4.U
  val divu = 5.U
  val rem = 6.U
  val remu = 7.U
}//mul对应乘法，mulh对应有符号乘法高位，mulhsu对应有符号乘法高位无符号，mulhum对应无符号乘法高位，div对应除法，divu对应无符号除法，rem对应取余，remu对应无符号取余

object InstructionsTypeB {
  val beq = "b000".U
  val bne = "b001".U
  val blt = "b100".U
  val bge = "b101".U
  val bltu = "b110".U
  val bgeu = "b111".U
}//beq对应等于跳转，bne对应不等于跳转，blt对应小于跳转，bge对应大于等于跳转，bltu对应无符号小于跳转，bgeu对应无符号大于等于跳转

object InstructionsTypeCSR {
  val csrrw = "b001".U
  val csrrs = "b010".U
  val csrrc = "b011".U
  val csrrwi = "b101".U
  val csrrsi = "b110".U
  val csrrci = "b111".U
}//csrrw对应读写CSR，csrrs对应读置位CSR，csrrc对应读清除CSR，csrrwi对应立即数读写CSR，csrrsi对应立即数读置位CSR，csrrci对应立即数读清除CSR

object InstructionsNop {
  val nop = 0x00000013L.U(Parameters.DataWidth)
}//nop对应空操作指令 NOP 的机器码

object InstructionsRet {
  val mret = 0x30200073L.U(Parameters.DataWidth)
  val ret = 0x00008067L.U(Parameters.DataWidth)
}//mret对应机器模式返回指令，ret对应返回指令

object InstructionsEnv {
  val ecall = 0x00000073L.U(Parameters.DataWidth)
  val ebreak = 0x00100073L.U(Parameters.DataWidth)
}//ecall对应环境调用指令，ebreak对应断点指令

object ALUOp1Source {
  val Register = 0.U(1.W)
  val InstructionAddress = 1.U(1.W)
}//ALUOp1Source 对应 ALU 的第一个操作数来源，Register 表示来自寄存器(控制信号位0），InstructionAddress 表示来自指令地址（控制信号为1）

object ALUOp2Source {
  val Register = 0.U(1.W)
  val Immediate = 1.U(1.W)
}//ALUOp2Source 对应 ALU 的第二个操作数来源，Register 表示来自寄存器(控制信号位0），Immediate 表示来自立即数（控制信号为1）

object RegWriteSource {
  val ALUResult = 0.U(2.W)
  val Memory = 1.U(2.W)
  //val CSR = 2.U(2.W)
  val NextInstructionAddress = 3.U(2.W)
}//RegWriteSource 对应写回寄存器的来源，ALUResult 表示来自 ALU 结果(控制信号位00），Memory 表示来自内存（控制信号为01），NextInstructionAddress 表示来自下一条指令地址（控制信号为11）

class InstructionDecode extends Module {
  val io = IO(new Bundle {
    val instruction = Input(UInt(Parameters.InstructionWidth))//输入的指令

    val regs_reg1_read_address = Output(UInt(Parameters.PhysicalRegisterAddrWidth))//读取哪个源寄存器
    val regs_reg2_read_address = Output(UInt(Parameters.PhysicalRegisterAddrWidth))//读取哪个源寄存器
    val ex_immediate = Output(UInt(Parameters.DataWidth))//提取出的立即数
    val ex_aluop1_source = Output(UInt(1.W))//ALU第一个操作数的来源，0表示来自寄存器，1表示来自指令地址
    val ex_aluop2_source = Output(UInt(1.W))//ALU第二个操作数的来源，0表示来自寄存器，1表示来自立即数
    val memory_read_enable = Output(Bool())//是否需要从内存读取数据
    val memory_write_enable = Output(Bool())//是否需要向内存写入数据
    val wb_reg_write_source = Output(UInt(2.W))//写回寄存器的来源，00表示来自ALU结果，01表示来自内存，11表示来自下一条指令地址
    val reg_write_enable = Output(Bool())//是否需要写回通用寄存器
    val reg_write_address = Output(UInt(Parameters.PhysicalRegisterAddrWidth))//写回哪个通用寄存器
  })
  val opcode = io.instruction(6, 0)
  val funct3 = io.instruction(14, 12)
  val funct7 = io.instruction(31, 25)
  val rd = io.instruction(11, 7)
  val rs1 = io.instruction(19, 15)
  val rs2 = io.instruction(24, 20)
//拆指令，对指令切片


//Mux(条件, 条件成立时的值, 条件不成立时的值)
  io.regs_reg1_read_address := Mux(opcode === Instructions.lui, 0.U(Parameters.PhysicalRegisterAddrWidth), rs1)
  //如果是 lui：第一个读取地址选 0   否则：第一个读取地址选 rs1

  io.regs_reg2_read_address := rs2
  val immediate = MuxLookup(//MuxLookup 表示根据 opcode 选择对应结果；没有匹配项时采用默认值。
    opcode,
    Cat(Fill(20, io.instruction(31)), io.instruction(31, 20)),
    IndexedSeq(
      InstructionTypes.I -> Cat(Fill(21, io.instruction(31)), io.instruction(30, 20)),
      InstructionTypes.L -> Cat(Fill(21, io.instruction(31)), io.instruction(30, 20)),
      Instructions.jalr -> Cat(Fill(21, io.instruction(31)), io.instruction(30, 20)),
      InstructionTypes.S -> Cat(Fill(21, io.instruction(31)), io.instruction(30, 25), io.instruction(11, 7)),
      InstructionTypes.B -> Cat(Fill(20, io.instruction(31)), io.instruction(7), io.instruction(30, 25), io.instruction(11, 8), 0.U(1.W)),
      Instructions.lui -> Cat(io.instruction(31, 12), 0.U(12.W)),
      Instructions.auipc -> Cat(io.instruction(31, 12), 0.U(12.W)),
      Instructions.jal -> Cat(Fill(12, io.instruction(31)), io.instruction(19, 12), io.instruction(20), io.instruction(30, 21), 0.U(1.W))
    )
  )
/*
val immediate = MuxLookup(
  opcode,
  默认值,
  IndexedSeq(
    类别1 -> 对应立即数,
    类别2 -> 对应立即数,
    ...
  )
  Fill(n, bit)将某一位重复 n 次。 Cat(a, b)把 a 放在高位、`b` 放在低位，拼成一个数
  | 指令类别 | 立即数构造 |

| `I`、`L`、`jalr` | 提取 `[31:20]`，符号扩展到 32 位 |
| `S` | 拼接 `[31:25]`、`[11:7]`，再符号扩展 |
| `B` | 按分支编码重新拼接，最低位补 0，再符号扩展 |
| `lui`、`auipc` | 取 `[31:12]`，低 12 位补 0 |
| `jal` | 按跳转编码重新拼接，最低位补 0，再符号扩展 |
)
*/



  io.ex_immediate := immediate
  io.ex_aluop1_source := Mux(
    opcode === Instructions.auipc || opcode === InstructionTypes.B || opcode === Instructions.jal,
    ALUOp1Source.InstructionAddress,
    ALUOp1Source.Register
  )/*
| 指令 | ALU 要计算什么 | 第一个输入 |

| `add` | 寄存器值 + 寄存器值 | 寄存器 |
| `addi` | 寄存器值 + 立即数 | 寄存器 |
| `lw`、`sw` | 基址寄存器值 + 偏移量 | 寄存器 |
| `auipc` | PC + 立即数 | PC |
| `B` 类分支 | PC + 分支偏移量 | PC |
| `jal` | PC + 跳转偏移量 | PC |
| `jalr` | 基址寄存器值 + 偏移量，随后清除结果最低位 | 寄存器 |*/




  // lab1(InstructionDecode)


/*究竟要补什么，从前面来看，很明显补充对ALU第二个操作数来源
| 指令 | ALU 计算 | 第二个输入 |
| `add x3, x1, x2` | `x1 + x2` | 寄存器 |
| `sub x3, x1, x2` | `x1 - x2` | 寄存器 |
| `addi x3, x1, 5` | `x1 + 5` | 立即数 |
| `lw x3, 8(x1)` | 地址为 `x1 + 8` | 立即数 |
| `sw x3, 8(x1)` | 地址为 `x1 + 8` | 立即数 |
| `beq x1, x2, offset` | 目标地址为 `PC + offset` | 立即数 |
| `jal` | 目标地址为 `PC + offset` | 立即数 |  
*/

  io.ex_aluop2_source := Mux(
    opcode === InstructionTypes.RM, 
    ALUOp2Source.Register,
    ALUOp2Source.Immediate
  )
/*
| 指令类别 | 读取数据内存 | 写入数据内存 |
| `L`，例如 `lw` | 是 | 否 |
| `S`，例如 `sw` | 否 | 是 |
| 算术、分支、跳转等 | 否 | 否 |
*/  
  io.memory_read_enable := (opcode === InstructionTypes.L)
  io.memory_write_enable := (opcode === InstructionTypes.S)


  io.wb_reg_write_source := Mux(//写回寄存器的来源
    opcode === InstructionTypes.L,     //就是判断“当前指令是否属于加载指令”
    RegWriteSource.Memory,
    Mux(
      opcode === Instructions.jal || opcode === Instructions.jalr,
      RegWriteSource.NextInstructionAddress,
      RegWriteSource.ALUResult
    )
  )
  /*
| 条件 | 写回来源 |
| 加载类 `L` | 内存读取数据 |
| `jal` 或 `jalr` | 当前 PC + 4 |
| 其他情况 | ALU 结果 |
  */



  // lab1(InstructionDecode) end



  io.reg_write_enable := (opcode === InstructionTypes.RM) || (opcode === InstructionTypes.I) ||
    (opcode === InstructionTypes.L) || (opcode === Instructions.auipc) || (opcode === Instructions.lui) ||
    (opcode === Instructions.jal) || (opcode === Instructions.jalr)

/*
最后两项：是否写寄存器、写哪里
| 指令 | 是否写通用寄存器 | 原因 |
| `add`、`addi` | 是 | 保存计算结果 |
| `lw` | 是 | 保存内存读取的数据 |
| `lui`、`auipc` | 是 | 保存构造或计算结果 |
| `jal`、`jalr` | 是 | 保存返回地址 |
| `sw` | 否 | 写的是内存 |
| `beq` | 否 | 控制是否跳转 |
*/    
  io.reg_write_address := io.instruction(11, 7)//把 rd 字段作为目标寄存器编号输出





  /*
  总的表为

| 指令示例 | ALU 第二个输入 | 读内存 | 写内存 | 写回来源 | 写寄存器 |
| `add x3,x1,x2` | 寄存器 | 否 | 否 | ALU | 是 |
| `addi x3,x1,5` | 立即数 | 否 | 否 | ALU | 是 |
| `lw x3,8(x1)` | 立即数 | 是 | 否 | 内存 | 是 |
| `sw x3,8(x1)` | 立即数 | 否 | 是 | 不使用 | 否 |
| `beq x1,x2,offset` | 立即数 | 否 | 否 | 不使用 | 否 |
| `jal x1,offset` | 立即数 | 否 | 否 | PC + 4 | 是 |
| `jalr x1,8(x2)` | 立即数 | 否 | 否 | PC + 4 | 是 |
  */
}
