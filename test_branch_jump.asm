.text
.globl main
main:
    # Initialize loop counter
    addi $t0, $zero, 0
    addi $t1, $zero, 3
    
loop:
    # Check condition
    beq $t0, $t1, end_loop
    
    # Body of loop
    addi $t0, $t0, 1
    
    # Jump to function
    jal my_func
    
    # Jump back to loop start
    j loop

my_func:
    # Do nothing, just return
    jr $ra
    
end_loop:
    # Exit
    li $v0, 10
    syscall
