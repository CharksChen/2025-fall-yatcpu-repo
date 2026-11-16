
/*
整机小程序：先把内存0x4设为0xDEADBEEF，调用汇编enable_interrupt配置CSR，收到中断后处理函数把同地址改为0x2022。
*/
extern void enable_interrupt();

// 汇编入口将mepc与mcause传入这两个参数，本例只改写内存。
void trap_handler(void *epc, unsigned int cause){
    *((int*)0x4) = 0x2022;
}

int main(){
    *((int*)0x4) = 0xDEADBEEF;
    // 在init.S中为mtvec设置陷入入口并使能中断。
    enable_interrupt();
    for(;;);
}