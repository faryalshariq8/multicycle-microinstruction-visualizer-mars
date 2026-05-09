.data
.text
.globl main
main:
    # Initialize some registers
    addi $t0, $zero, 10
    addi $t1, $zero, 20
    
    # Arithmetic
    add $t2, $t0, $t1
    sub $t3, $t1, $t0
    
    # Logical
    and $t4, $t2, $t3
    or  $t5, $t2, $t3
    
    # Multiplication
    mult $t0, $t1
    mflo $t6
    
    # Shift
    sll $t7, $t0, 2
    
    # Exit loop
    j end
end:
    syscall
