.text
.globl main
main:
    add $t0, $t1, $t2
    lw $t3, 0($t0)
    sw $t3, 4($t0)
    beq $t0, $t1, main
    j main
