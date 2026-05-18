package mars.tools;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.Observable;

import mars.Globals;
import mars.mips.hardware.AccessNotice;
import mars.mips.hardware.MemoryAccessNotice;
import mars.mips.hardware.Memory;
import mars.mips.hardware.AddressErrorException;
import mars.ProgramStatement;

public class MicroInstructionVisualizer extends AbstractMarsToolAndApplication {
    private static final long serialVersionUID = 1L;
    private static String heading = "Micro-Instruction Visualizer";
    private static String version = " Version 3.0";

    private JPanel mainPanel;
    private JLabel stateLabel;
    private JTextArea activeSignalsArea;
    private JTextArea internalRegistersArea;
    private JButton microStepButton;
    private DatapathPanel datapathPanel;
    private StageProgressPanel stagePanel;
    private DefaultTableModel instructionTableModel;
    private JTable instructionTable;

    private int currentState = 0; 
    private int maxState = 4; 
    private int currentAddress = -1;
    
    private String currentInstructionBinary = "";
    private String currentMnemonic = "";
    private String savedHexPC = "";
    private String savedNextPC = "";

    public MicroInstructionVisualizer(String title, String heading) {
        super(title, heading);
    }

    public MicroInstructionVisualizer() {
        super(heading + ", " + version, heading);
    }

    @Override
    public String getName() {
        return "Micro-Instruction Visualizer";
    }

    // =========================== ADD THIS METHOD ===========================
// Place this inside MicroInstructionVisualizer class
// (outside all inner classes)

    private int getVisualStage() {
        int opcode = 0;

        if (currentInstructionBinary != null && currentInstructionBinary.length() == 32) {
            opcode = Integer.parseInt(currentInstructionBinary.substring(0, 6), 2);
        }

        boolean isLoad = (opcode == 35 || opcode == 32 || opcode == 33
                || opcode == 36 || opcode == 37);

        boolean isStore = (opcode == 43 || opcode == 40 || opcode == 41);

        if (currentState == 3) {
            if (isLoad || isStore) {
                return 3; // Memory stage
            }
            return 4; // WriteBack stage
        }
        if (currentState == 4) {
            return 4;
        }
        return currentState;
    }

    private String regName(int reg) {
        String[] names = {
            "$zero", "$at", "$v0", "$v1",
            "$a0", "$a1", "$a2", "$a3",
            "$t0", "$t1", "$t2", "$t3",
            "$t4", "$t5", "$t6", "$t7",
            "$s0", "$s1", "$s2", "$s3",
            "$s4", "$s5", "$s6", "$s7",
            "$t8", "$t9", "$k0", "$k1",
            "$gp", "$sp", "$fp", "$ra"
        };

        if (reg >= 0 && reg < names.length) {
            return names[reg];
        }

        return "$r" + reg;
    }

    @Override
    protected JComponent buildMainDisplayArea() {
        mainPanel = new JPanel(new BorderLayout());

        JPanel infoPanel = new JPanel(new BorderLayout());
        stateLabel = new JLabel("Current State: Waiting for Fetch...");
        stateLabel.setFont(new Font("Arial", Font.BOLD, 16));
        infoPanel.add(stateLabel, BorderLayout.NORTH);
        
        JPanel textInfoPanel = new JPanel(new GridLayout(1, 2, 5, 5));
        
        activeSignalsArea = new JTextArea(6, 30);
        activeSignalsArea.setEditable(false);
        activeSignalsArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        activeSignalsArea.setBorder(BorderFactory.createTitledBorder("Active Control Signals & Micro-Ops"));
        textInfoPanel.add(new JScrollPane(activeSignalsArea));
        
        internalRegistersArea = new JTextArea(6, 20);
        internalRegistersArea.setEditable(false);
        internalRegistersArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        internalRegistersArea.setBorder(BorderFactory.createTitledBorder("Internal State"));
        textInfoPanel.add(new JScrollPane(internalRegistersArea));
        
        infoPanel.add(textInfoPanel, BorderLayout.CENTER);
        mainPanel.add(infoPanel, BorderLayout.NORTH);

        stagePanel = new StageProgressPanel();
        stagePanel.setPreferredSize(new Dimension(900, 50));

        datapathPanel = new DatapathPanel();
        datapathPanel.setPreferredSize(new Dimension(900, 300));
        datapathPanel.setBackground(Color.WHITE);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(stagePanel, BorderLayout.NORTH);
        centerPanel.add(datapathPanel, BorderLayout.CENTER);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        String[] cols = {"#", "Address", "Instruction", "Opcode", "rs", "rt", "rd/imm", "Cycles"};
        instructionTableModel = new DefaultTableModel(cols, 0);
        instructionTable = new JTable(instructionTableModel);
        instructionTable.setFont(new Font("Monospaced", Font.PLAIN, 12));
        instructionTable.setRowHeight(24);
        instructionTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        JScrollPane tableScroll = new JScrollPane(instructionTable);
        tableScroll.setPreferredSize(new Dimension(900, 120));
        tableScroll.setBorder(BorderFactory.createTitledBorder("Instruction Execution Log"));

        JPanel controlPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel();
        microStepButton = new JButton("Micro-Step");
        microStepButton.setEnabled(false);
        microStepButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                advanceMicroStep();
            }
        });
        buttonPanel.add(microStepButton);
        controlPanel.add(buttonPanel, BorderLayout.NORTH);
        controlPanel.add(tableScroll, BorderLayout.SOUTH);
        mainPanel.add(controlPanel, BorderLayout.SOUTH);

        return mainPanel;
    }

    @Override
    protected void addAsObserver() {
        addAsObserver(Memory.textBaseAddress, Memory.textLimitAddress);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void processMIPSUpdate(Observable resource, AccessNotice notice) {
        if (!notice.accessIsFromMIPS()) return;
        if (notice.getAccessType() != AccessNotice.READ) return;

        MemoryAccessNotice man = (MemoryAccessNotice) notice;
        int address = man.getAddress();

        if (address == currentAddress) return;
        currentAddress = address;

        try {
            ProgramStatement stmt = Memory.getInstance().getStatement(address);
            if (stmt != null) {
                final String bin = stmt.getMachineStatement();
                final String mnemonic = stmt.getBasicAssemblyStatement();
                final int currentPC = mars.mips.hardware.RegisterFile.getProgramCounter();
                final String hexPC = mars.util.Binary.intToHexString(currentPC);
                final String nextPC = mars.util.Binary.intToHexString(currentPC + 4);

                int opcode = 0;
                int rs = 0;
                int rt = 0;
                int rd = 0;
                int imm = 0;
                if (bin != null && bin.length() == 32) {
                    opcode = Integer.parseInt(bin.substring(0, 6), 2);
                    rs = Integer.parseInt(bin.substring(6, 11), 2);
                    rt = Integer.parseInt(bin.substring(11, 16), 2);
                    rd = Integer.parseInt(bin.substring(16, 21), 2);
                    imm = (short) Integer.parseInt(bin.substring(16, 32), 2);
                }
                final int finalOpcode = opcode;
                final int finalRs = rs;
                final int finalRt = rt;
                final int finalRd = rd;
                final int finalImm = imm;
                final int finalCycles;
                if (finalOpcode == 2 || finalOpcode == 3) {
                    finalCycles = 2;
                } else if (finalOpcode == 4 || finalOpcode == 5) {
                    finalCycles = 3;
                } else if (finalOpcode == 35 || finalOpcode == 43) {
                    finalCycles = 4;
                } else {
                    finalCycles = 5;
                }

                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        currentInstructionBinary = bin;
                        currentMnemonic =
                            mnemonic
                                .replace("$" + finalRs, regName(finalRs))
                                .replace("$" + finalRt, regName(finalRt))
                                .replace("$" + finalRd, regName(finalRd));
                        savedHexPC = hexPC;
                        savedNextPC = nextPC;

                        int rowNum = instructionTableModel.getRowCount() + 1;
                        if (instructionTableModel.getRowCount() > 0) {
                            Object lastAddr = instructionTableModel.getValueAt(
                                    instructionTableModel.getRowCount() - 1,
                                    1
                            );

                            if (savedHexPC.equals(lastAddr)) {
                                return;
                            }
                        }
                        instructionTableModel.addRow(new Object[]{
                            rowNum,
                            savedHexPC,
                            mnemonic,
                            finalOpcode,
                            finalRs,
                            finalRt,
                            (finalOpcode == 0 ? finalRd : finalImm),
                            finalCycles
                        });

                        currentState = 0;
                        microStepButton.setEnabled(true);
                        updateVisualsForState();
                    }
                });
            }
        } catch (AddressErrorException e) {
            e.printStackTrace();
        }
    }

    private void advanceMicroStep() {
        currentState++;
        if (currentState > maxState) {
            currentState = 0;
            microStepButton.setEnabled(false);
            stateLabel.setText(
                    "Current State: Execution complete. Step MARS for next instruction."
            );
            activeSignalsArea.setText("");
            // IMPORTANT FIX
            internalRegistersArea.setText("");
            datapathPanel.setActiveBlocks(new String[]{});
            datapathPanel.setActiveWires(new String[]{});
            datapathPanel.repaint();
            stagePanel.repaint();
        } else {

            updateVisualsForState();
        }
    }

    private void updateVisualsForState() {
        int opcode = 0;
        int func = 0;
        int rs = 0;
        int rt = 0;
        int rd = 0;
        int shamt = 0;
        int imm = 0;
        if (currentInstructionBinary != null && currentInstructionBinary.length() == 32) {
            opcode = Integer.parseInt(currentInstructionBinary.substring(0, 6), 2);
            rs = Integer.parseInt(currentInstructionBinary.substring(6, 11), 2);
            rt = Integer.parseInt(currentInstructionBinary.substring(11, 16), 2);
            rd = Integer.parseInt(currentInstructionBinary.substring(16, 21), 2);
            shamt = Integer.parseInt(currentInstructionBinary.substring(21, 26), 2);
            func = Integer.parseInt(currentInstructionBinary.substring(26, 32), 2);
            imm = (short) Integer.parseInt(currentInstructionBinary.substring(16, 32), 2);
        }

        // Categorize instruction
        boolean isRType = (opcode == 0);
        boolean isLoad = (opcode == 35 || opcode == 32 || opcode == 33 || opcode == 36 || opcode == 37); // lw, lb, lh, lbu, lhu
        boolean isStore = (opcode == 43 || opcode == 40 || opcode == 41); // sw, sb, sh
        boolean isBranch = (opcode == 4 || opcode == 5); // beq, bne
        boolean isJump = (opcode == 2 || opcode == 3); // j, jal
        boolean isJAL = (opcode == 3);
        boolean isITypeArith = (opcode == 8 || opcode == 9 || opcode == 10 || opcode == 11 || opcode == 12 || opcode == 13 || opcode == 14 || opcode == 15); // addi, slti, andi, ori, xori, lui

        switch(currentState) {
            case 0:
                maxState = 4; // default
                stateLabel.setText("Current State: Cycle 1 (Fetch) - " + currentMnemonic);
                activeSignalsArea.setText("Active Control Signals:\nMemRead=1, IorD=0, IRWrite=1, PCWrite=1\n\nMicro-ops:\nMAR <- PC; Read Memory; IR <- MDR; PC <- PC + 4");
                if (currentInstructionBinary.length() == 32) {
                    internalRegistersArea.setText(
                        "PC: " + savedHexPC + "\nInstruction: " + currentMnemonic +
                        "\nOpcode: " + opcode +
                        " | rs: " + regName(rs) +
                        " | rt: " + regName(rt) +

                        "\nrd: " + regName(rd) +
                        " | shamt: " + shamt +
                        " | funct: " + func +
                        "\nImmediate: " + imm +
                        "\nIR (binary): " + currentInstructionBinary.substring(0,8) + " " +
                        currentInstructionBinary.substring(8,16) + " " +
                        currentInstructionBinary.substring(16,24) + " " +
                        currentInstructionBinary.substring(24,32)
                    );
                } else {
                    internalRegistersArea.setText("PC: " + savedNextPC + "\nIR: " + currentInstructionBinary);
                }
                datapathPanel.setActiveBlocks(new String[]{"PC", "Memory", "IR"});
                datapathPanel.setActiveWires(new String[]{"PC_to_Mem", "Mem_to_IR"});
                break;
            case 1:
                stateLabel.setText("Current State: Cycle 2 (Decode) - " + currentMnemonic);
                if (isJAL) {
                    activeSignalsArea.setText(
                        "Active Control Signals:\nPCWrite=1, Jump=1, RegWrite=1, RegDst=$ra\n\n" +
                        "Micro-ops:\n$ra <- PC+4 (saved)\nPC <- Jump Address (PC[31:28] || target || 00)"
                    );
                    datapathPanel.setActiveBlocks(new String[]{"PC", "RegFile"});
                    datapathPanel.setActiveWires(new String[]{"IR_to_PC", "JAL_to_Reg"});
                    maxState = 1;
                } else if (isJump) {
                    activeSignalsArea.setText("Active Control Signals:\nPCWrite=1, Jump=1\n\nMicro-ops:\nPC <- Jump Address");
                    datapathPanel.setActiveBlocks(new String[]{"PC", "IR"});
                    datapathPanel.setActiveWires(new String[]{"IR_to_PC"});
                    maxState = 1; // Jump finishes in cycle 2
                } else {
                    activeSignalsArea.setText(
                        "Active Control Signals:\nALUSrcA=0, ALUSrcB=11\n\n" +
                        "Micro-ops:\nA <- Reg[rs]; B <- Reg[rt]; ALUOut <- PC + (sign-ext(imm) << 2)"
                    );
                    internalRegistersArea.setText(
                        "PC: " + savedNextPC +
                        "\nIR: " + currentInstructionBinary +
                        "\nA: Reg[rs]\nB: Reg[rt]"
                    );
                    datapathPanel.setActiveBlocks(
                        new String[]{
                            "IR",
                            "MDR",
                            "RegFile",
                            "A",
                            "B",
                            "ALUOut"
                        }
                    );
                    datapathPanel.setActiveWires(
                        new String[]{
                            "IR_to_Reg",
                            "MDR_to_Reg",
                            "Reg_to_A",
                            "Reg_to_B"
                        }
                    );
                }
                break;
            case 2:
                stateLabel.setText("Current State: Cycle 3 (Execute) - " + currentMnemonic);
                if (isRType || isITypeArith) {
                    activeSignalsArea.setText("Active Control Signals:\nALUSrcA=1, ALUSrcB=00 (or 10 for imm), ALUOp=10\n\nMicro-ops:\nALUOut <- A op B");
                    datapathPanel.setActiveBlocks(new String[]{"A", "B", "ALU", "ALUOut"});
                    datapathPanel.setActiveWires(new String[]{"A_to_ALU", "B_to_ALU", "ALU_to_ALUOut"});
                    maxState = 3; 
                } else if (isLoad || isStore) {
                    activeSignalsArea.setText("Active Control Signals:\nALUSrcA=1, ALUSrcB=10, ALUOp=00\n\nMicro-ops:\nALUOut <- A + sign-ext(imm)");
                    datapathPanel.setActiveBlocks(
                        new String[]{"A", "ALU", "ALUOut"}
                    );
                    datapathPanel.setActiveWires(
                        new String[]{
                            "A_to_ALU",
                            "ALU_to_ALUOut"
                        }
                    );
                    if (isStore) maxState = 3; 
                } else if (isBranch) {
                    activeSignalsArea.setText("Active Control Signals:\nALUSrcA=1, ALUSrcB=00, ALUOp=01, PCWriteCond=1\n\nMicro-ops:\nif (A == B) PC <- ALUOut");
                    datapathPanel.setActiveBlocks(new String[]{"A", "B", "ALU", "ALUOut", "PC"});
                    datapathPanel.setActiveWires(new String[]{"A_to_ALU", "B_to_ALU", "ALUOut_to_PC"});
                    maxState = 2; // BEQ finishes here
                }
                internalRegistersArea.setText("PC: " + savedNextPC + "\nIR: " + currentInstructionBinary + "\nALUOut: Computed...");
                break;
            case 3:
                if (isRType || isITypeArith) { 
                    stateLabel.setText("Current State: Cycle 4 (WriteBack) - " + currentMnemonic);
                    activeSignalsArea.setText("Active Control Signals:\nRegDst=1 (or 0), RegWrite=1, MemtoReg=0\n\nMicro-ops:\nReg[rd] <- ALUOut");
                    datapathPanel.setActiveBlocks(new String[]{"ALUOut", "RegFile"});
                    datapathPanel.setActiveWires(new String[]{"ALUOut_to_Reg"});
                } else if (isLoad) { 
                    stateLabel.setText("Current State: Cycle 4 (Memory Access) - " + currentMnemonic);
                    activeSignalsArea.setText("Active Control Signals:\nMemRead=1, IorD=1\n\nMicro-ops:\nMDR <- Memory[ALUOut]");
                    datapathPanel.setActiveBlocks(new String[]{"ALUOut", "Memory", "MDR"});
                    datapathPanel.setActiveWires(new String[]{"ALUOut_to_Mem", "Mem_to_MDR"});
                } else if (isStore) { 
                    stateLabel.setText("Current State: Cycle 4 (Memory Access) - " + currentMnemonic);
                    activeSignalsArea.setText("Active Control Signals:\nMemWrite=1, IorD=1\n\nMicro-ops:\nMemory[ALUOut] <- B");
                    datapathPanel.setActiveBlocks(new String[]{"ALUOut", "B", "Memory"});
                    datapathPanel.setActiveWires(new String[]{"ALUOut_to_Mem", "B_to_Mem"});
                }
                break;
            case 4:
                if (isLoad) { 
                    stateLabel.setText("Current State: Cycle 5 (WriteBack) - " + currentMnemonic);
                    activeSignalsArea.setText("Active Control Signals:\nRegDst=0, RegWrite=1, MemtoReg=1\n\nMicro-ops:\nReg[rt] <- MDR");
                    datapathPanel.setActiveBlocks(new String[]{"MDR", "RegFile"});
                    datapathPanel.setActiveWires(new String[]{"MDR_to_Reg"});
                }
                break;
        }
        datapathPanel.repaint();
        stagePanel.repaint();
    }

    @Override
    protected void reset() {
        SwingUtilities.invokeLater(() -> {
            currentState = 0;
            maxState = 4;
            currentAddress = -1;
            currentInstructionBinary = "";
            currentMnemonic = "";
            savedHexPC = "";
            savedNextPC = "";

            stateLabel.setText("Current State: Waiting for Fetch...");
            activeSignalsArea.setText("");
            internalRegistersArea.setText("");
            microStepButton.setEnabled(false);
            datapathPanel.setActiveBlocks(new String[0]);
            datapathPanel.setActiveWires(new String[0]);
            datapathPanel.repaint();
            stagePanel.repaint();
            if (instructionTableModel != null) {
                instructionTableModel.setRowCount(0);
            }
        });
    }

    class StageProgressPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            String[] stages = {"Fetch", "Decode", "Execute", "Memory", "WriteBack"};
            int width = getWidth();
            int height = getHeight();
            int cellWidth = Math.max(120, (width - 40) / stages.length);
            int x = 20;
            int y = 10;

            for (int i = 0; i < stages.length; i++) {
                Color fill = (i == currentState) ? new Color(70, 130, 180) : new Color(200, 200, 200);
                Color text = (i == currentState) ? Color.WHITE : Color.BLACK;
                g2d.setColor(fill);
                g2d.fillRoundRect(x, y, cellWidth, 30, 12, 12);
                g2d.setColor(Color.DARK_GRAY);
                g2d.drawRoundRect(x, y, cellWidth, 30, 12, 12);
                g2d.setColor(text);
                FontMetrics fm = g2d.getFontMetrics();
                int textX = x + (cellWidth - fm.stringWidth(stages[i])) / 2;
                int textY = y + ((30 - fm.getHeight()) / 2) + fm.getAscent();
                g2d.drawString(stages[i], textX, textY);

                if (i < stages.length - 1) {
                    int arrowX = x + cellWidth;
                    int arrowY = y + 15;
                    int arrowSize = 8;
                    g2d.setColor(i < getVisualStage()
                            ? new Color(70, 130, 180)
                            : Color.GRAY);
                    g2d.fillPolygon(
                            new int[]{
                                    arrowX + 4,
                                    arrowX + 4 + arrowSize,
                                    arrowX + 4
                            },
                            new int[]{
                                    arrowY - arrowSize / 2,
                                    arrowY,
                                    arrowY + arrowSize / 2
                            },
                            3
                    );
                }
                x += cellWidth + 10;
            }
        }
    }

    class DatapathPanel extends JPanel {
        private String[] activeBlocks = new String[]{};
        private String[] activeWires = new String[]{};

        public void setActiveBlocks(String[] blocks) { this.activeBlocks = blocks; }
        public void setActiveWires(String[] wires) { this.activeWires = wires; }

        private boolean isActiveBlock(String name) {
            for (String b : activeBlocks) if (b.equals(name)) return true;
            return false;
        }

        private boolean isActiveWire(String name) {
            for (String w : activeWires) if (w.equals(name)) return true;
            return false;
        }

        private void drawBlock(Graphics g, String name, int x, int y, int w, int h) {
            if (isActiveBlock(name)) {
                g.setColor(new Color(255, 255, 153)); 
                g.fillRect(x, y, w, h);
                g.setColor(Color.RED);
                ((Graphics2D)g).setStroke(new BasicStroke(2));
                g.drawRect(x, y, w, h);
                ((Graphics2D)g).setStroke(new BasicStroke(1));
            } else {
                g.setColor(new Color(220, 220, 220)); 
                g.fillRect(x, y, w, h);
                g.setColor(Color.BLACK);
                g.drawRect(x, y, w, h);
            }
            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.BOLD, 12));
            FontMetrics fm = g.getFontMetrics();
            int strX = x + (w - fm.stringWidth(name)) / 2;
            int strY = y + ((h - fm.getHeight()) / 2) + fm.getAscent();
            g.drawString(name, strX, strY);
        }

        private void drawALU(Graphics2D g2d, int x, int y, boolean active) {
            Polygon alu = new Polygon( 
                new int[]{x, x + 60, x + 60, x + 45, x + 15, x}, 
                new int[]{y + 15, y + 15, y + 65, y + 80, y + 65, y + 80}, 
                6 
            );
            g2d.setColor(active ? new Color(255, 255, 153) : new Color(220, 220, 220));
            g2d.fillPolygon(alu);
            g2d.setColor(active ? Color.RED : Color.BLACK);
            g2d.setStroke(new BasicStroke(active ? 2f : 1f));
            g2d.drawPolygon(alu);
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            FontMetrics fm = g2d.getFontMetrics();
            int strX = x + 30 - fm.stringWidth("ALU") / 2;
            int strY = y + 45;
            g2d.drawString("ALU", strX, strY);
        }

        private void drawWire(Graphics2D g, String name,
                      int[] xPoints, int[] yPoints) {
            g.setColor(isActiveWire(name) ? Color.RED : Color.BLACK);
            // SMOOTH ROUNDED WIRES
            g.setStroke(new BasicStroke(
                    isActiveWire(name) ? 3f : 1f,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
            ));
            for (int i = 0; i < xPoints.length - 1; i++) {
                g.drawLine(
                        xPoints[i],
                        yPoints[i],
                        xPoints[i + 1],
                        yPoints[i + 1]
                );
            }
            // Arrowhead
            if (xPoints.length >= 2) {
                drawArrow(
                        g,
                        xPoints[xPoints.length - 2],
                        yPoints[yPoints.length - 2],
                        xPoints[xPoints.length - 1],
                        yPoints[yPoints.length - 1]
                );
            }
            g.setStroke(new BasicStroke(1));
        }

        private void drawArrow(Graphics2D g2d, int x1, int y1, int x2, int y2) {
            int arrowSize = 8;
            double angle = Math.atan2(y2 - y1, x2 - x1);
            int[] xPoints = {
                x2,
                (int)(x2 - arrowSize * Math.cos(angle - Math.PI / 6)),
                (int)(x2 - arrowSize * Math.cos(angle + Math.PI / 6))
            };
            int[] yPoints = {
                y2,
                (int)(y2 - arrowSize * Math.sin(angle - Math.PI / 6)),
                (int)(y2 - arrowSize * Math.sin(angle + Math.PI / 6))
            };
            g2d.fillPolygon(xPoints, yPoints, 3);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            );

            drawBlock(g, "PC", 50, 100, 40, 60);
            drawBlock(g, "Memory", 150, 70, 100, 120);
            drawBlock(g, "IR", 300, 50, 80, 50);
            drawBlock(g, "MDR", 300, 140, 80, 50);
            drawBlock(g, "RegFile", 450, 100, 100, 120);
            drawBlock(g, "A", 600, 70, 40, 40);
            drawBlock(g, "B", 600, 150, 40, 40);

            drawALU(g2d, 700, 100, isActiveBlock("ALU"));

            drawBlock(g, "ALUOut", 800, 120, 60, 40);

            // Draw Wires with routing

            // ================= WIRES =================

            // PC -> Memory
            drawWire(
                g2d,
                "PC_to_Mem",
                new int[]{90, 150},
                new int[]{130, 130}
            );

            // Memory -> IR
            drawWire(
                g2d,
                "Mem_to_IR",
                new int[]{250, 280, 280, 300},
                new int[]{130, 130, 75, 75}
            );

            // Memory -> MDR
            drawWire(
                g2d,
                "Mem_to_MDR",
                new int[]{250, 280, 280, 300},
                new int[]{130, 130, 165, 165}
            );

            // IR -> RegFile
            drawWire(
                g2d,
                "IR_to_Reg",
                new int[]{380, 420, 420, 450},
                new int[]{75, 75, 130, 130}
            );

            // MDR -> RegFile
            drawWire(
                g2d,
                "MDR_to_Reg",
                new int[]{380, 450},
                new int[]{165, 165}
            );
            // ================= TOP CONTROL BUS =================

            // Main top bus
            g2d.drawLine(40, 20, 900, 20);

            // ONE input into Memory (stop before block)
            g2d.drawLine(215, 20, 215, 68);
            drawArrow(g2d, 215, 20, 215, 68);

            // ONE input into IR
            g2d.drawLine(340, 20, 340, 50);
            drawArrow(g2d, 340, 20, 340, 50);

           // ONE input into RegFile
            //g2d.drawLine(500, 20, 500, 100);
            //drawArrow(g2d, 500, 20, 500, 100);
            // ALUOut -> RegFile
            drawWire(
                g2d,
                "ALUOut_to_Reg",
                new int[]{830, 830, 500, 500},
                new int[]{120, 20, 20, 100}
            );

            // ================= REGFILE OUTPUTS =================

            // RegFile -> A
            drawWire(
                g2d,
                "Reg_to_A",
                new int[]{550, 575, 575, 600},
                new int[]{130, 130, 90, 90}
            );

            // RegFile -> B
            drawWire(
                g2d,
                "Reg_to_B",
                new int[]{550, 575, 575, 600},
                new int[]{130, 130, 170, 170}
            );

            // ================= ALU INPUTS =================

            // A -> ALU (90 degree clean wire)
            drawWire(
                g2d,
                "A_to_ALU",
                new int[]{640, 680, 680, 700},
                new int[]{90, 90, 110, 110}
            );

            // B -> ALU
            drawWire(
                g2d,
                "B_to_ALU",
                new int[]{640, 700},
                new int[]{170, 170}
            );

            // ALU -> ALUOut
            drawWire(
                g2d,
                "ALU_to_ALUOut",
                new int[]{760, 800},
                new int[]{140, 140}
            );

            // ================= FEEDBACK PATHS =================

            // ALUOut -> Memory
            drawWire(
                g2d,
                "ALUOut_to_Mem",
                new int[]{830, 830, 215},
                new int[]{120, 20, 20}
            );

            // ALUOut -> PC
            drawWire(
                g2d,
                "ALUOut_to_PC",
                new int[]{830, 830, 30, 30, 50},
                new int[]{120, 20, 20, 130, 130});

            // Jump path IR -> PC
            drawWire(
                g2d,
                "IR_to_PC",
                new int[]{340, 340, 30, 30, 50},
                new int[]{50, 20, 20, 130, 130}
            );
        }
    }
}