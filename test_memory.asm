.data
my_word: .word 0x12345678
my_array: .space 20

.text
.globl main
main:
    # Load address of data
    la $t0, my_word
    la $t1, my_array
    
    # Load word, halfword, byte
    lw $t2, 0($t0)
    lh $t3, 0($t0)
    lb $t4, 0($t0)
    
    # Store word, halfword, byte into array
    sw $t2, 0($t1)
    sh $t3, 4($t1)
    sb $t4, 8($t1)
    
    # Exit
    li $v0, 10
    syscall
