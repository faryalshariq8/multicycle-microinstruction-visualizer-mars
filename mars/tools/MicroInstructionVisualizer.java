package mars.tools;

import javax.swing.*;
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

        datapathPanel = new DatapathPanel();
        datapathPanel.setPreferredSize(new Dimension(900, 300));
        datapathPanel.setBackground(Color.WHITE);
        mainPanel.add(datapathPanel, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel();
        microStepButton = new JButton("Micro-Step");
        microStepButton.setEnabled(false);
        microStepButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                advanceMicroStep();
            }
        });
        controlPanel.add(microStepButton);
        mainPanel.add(controlPanel, BorderLayout.SOUTH);

        return mainPanel;
    }

    @Override
    protected void addAsObserver() {
        addAsObserver(Memory.textBaseAddress, Memory.textLimitAddress);
    }

    @Override
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
                
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        currentInstructionBinary = bin;
                        currentMnemonic = mnemonic;
                        savedHexPC = hexPC;
                        savedNextPC = nextPC;
                        
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
            stateLabel.setText("Current State: Execution complete. Step MARS for next instruction.");
            activeSignalsArea.setText("");
            datapathPanel.setActiveBlocks(new String[]{}); 
            datapathPanel.setActiveWires(new String[]{});
            datapathPanel.repaint();
        } else {
            updateVisualsForState();
        }
    }

    private void updateVisualsForState() {
        int opcode = 0;
        int func = 0;
        if (currentInstructionBinary != null && currentInstructionBinary.length() == 32) {
            opcode = Integer.parseInt(currentInstructionBinary.substring(0, 6), 2);
            func = Integer.parseInt(currentInstructionBinary.substring(26, 32), 2);
        }

        // Categorize instruction
        boolean isRType = (opcode == 0);
        boolean isLoad = (opcode == 35 || opcode == 32 || opcode == 33 || opcode == 36 || opcode == 37); // lw, lb, lh, lbu, lhu
        boolean isStore = (opcode == 43 || opcode == 40 || opcode == 41); // sw, sb, sh
        boolean isBranch = (opcode == 4 || opcode == 5); // beq, bne
        boolean isJump = (opcode == 2 || opcode == 3); // j, jal
        boolean isITypeArith = (opcode == 8 || opcode == 9 || opcode == 10 || opcode == 11 || opcode == 12 || opcode == 13 || opcode == 14 || opcode == 15); // addi, slti, andi, ori, xori, lui

        switch(currentState) {
            case 0:
                maxState = 4; // default
                stateLabel.setText("Current State: Cycle 1 (Fetch) - " + currentMnemonic);
                activeSignalsArea.setText("Active Control Signals:\nMemRead=1, IorD=0, IRWrite=1, PCWrite=1\n\nMicro-ops:\nMAR <- PC; Read Memory; IR <- MDR; PC <- PC + 4");
                internalRegistersArea.setText("PC: " + savedNextPC + "\nIR: " + currentInstructionBinary + "\nMAR: " + savedHexPC + "\nMDR: " + currentInstructionBinary);
                datapathPanel.setActiveBlocks(new String[]{"PC", "Memory", "IR"});
                datapathPanel.setActiveWires(new String[]{"PC_to_Mem", "Mem_to_IR"});
                break;
            case 1:
                stateLabel.setText("Current State: Cycle 2 (Decode) - " + currentMnemonic);
                activeSignalsArea.setText("Active Control Signals:\nALUSrcA=0, ALUSrcB=11\n\nMicro-ops:\nA <- Reg[rs]; B <- Reg[rt]; ALUOut <- PC + (sign-ext(imm) << 2)");
                internalRegistersArea.setText("PC: " + savedNextPC + "\nIR: " + currentInstructionBinary + "\nA: Reg[rs]\nB: Reg[rt]");
                datapathPanel.setActiveBlocks(new String[]{"RegFile", "A", "B", "ALUOut"});
                datapathPanel.setActiveWires(new String[]{"IR_to_Reg", "Reg_to_A", "Reg_to_B"});
                
                if (isJump) {
                    activeSignalsArea.setText("Active Control Signals:\nPCWrite=1, Jump=1\n\nMicro-ops:\nPC <- Jump Address");
                    datapathPanel.setActiveBlocks(new String[]{"PC"});
                    datapathPanel.setActiveWires(new String[]{"IR_to_PC"});
                    maxState = 1; // Jump finishes in cycle 2
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
                    datapathPanel.setActiveBlocks(new String[]{"A", "ALU", "ALUOut"});
                    datapathPanel.setActiveWires(new String[]{"A_to_ALU", "ALU_to_ALUOut"});
                    if (isStore) maxState = 3; 
                } else if (isBranch) {
                    activeSignalsArea.setText("Active Control Signals:\nALUSrcA=1, ALUSrcB=00, ALUOp=01, PCWriteCond=1\n\nMicro-ops:\nif (A == B) PC <- ALUOut");
                    datapathPanel.setActiveBlocks(new String[]{"A", "B", "ALU", "PC"});
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

        private void drawWire(Graphics2D g, String name, int[] xPoints, int[] yPoints) {
            if (isActiveWire(name)) {
                g.setColor(Color.RED);
                g.setStroke(new BasicStroke(3));
            } else {
                g.setColor(Color.BLACK);
                g.setStroke(new BasicStroke(1));
            }
            for (int i = 0; i < xPoints.length - 1; i++) {
                g.drawLine(xPoints[i], yPoints[i], xPoints[i+1], yPoints[i+1]);
            }
            // Reset stroke
            g.setStroke(new BasicStroke(1));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            drawBlock(g, "PC", 50, 100, 40, 60);
            drawBlock(g, "Memory", 150, 70, 100, 120);
            drawBlock(g, "IR", 300, 50, 80, 50);
            drawBlock(g, "MDR", 300, 140, 80, 50);
            drawBlock(g, "RegFile", 450, 100, 100, 120);
            drawBlock(g, "A", 600, 70, 40, 40);
            drawBlock(g, "B", 600, 150, 40, 40);
            drawBlock(g, "ALU", 700, 100, 60, 80);
            drawBlock(g, "ALUOut", 800, 120, 60, 40);

            // Draw Wires with routing
            drawWire(g2d, "PC_to_Mem", new int[]{90, 150}, new int[]{130, 130});
            drawWire(g2d, "Mem_to_IR", new int[]{250, 275, 275, 300}, new int[]{130, 130, 75, 75});
            drawWire(g2d, "Mem_to_MDR", new int[]{250, 275, 275, 300}, new int[]{130, 130, 165, 165});
            drawWire(g2d, "IR_to_Reg", new int[]{380, 450}, new int[]{75, 110});
            drawWire(g2d, "IR_to_PC", new int[]{380, 400, 400, 70, 70}, new int[]{50, 50, 20, 20, 100}); // Jump back
            drawWire(g2d, "MDR_to_Reg", new int[]{380, 450}, new int[]{165, 165});
            
            drawWire(g2d, "Reg_to_A", new int[]{550, 575, 575, 600}, new int[]{130, 130, 90, 90});
            drawWire(g2d, "Reg_to_B", new int[]{550, 575, 575, 600}, new int[]{130, 130, 170, 170});
            
            drawWire(g2d, "A_to_ALU", new int[]{640, 700}, new int[]{90, 110});
            drawWire(g2d, "B_to_ALU", new int[]{640, 700}, new int[]{170, 170});
            
            drawWire(g2d, "ALU_to_ALUOut", new int[]{760, 800}, new int[]{140, 140});
            
            drawWire(g2d, "ALUOut_to_Mem", new int[]{830, 830, 200, 200}, new int[]{120, 20, 20, 70});
            drawWire(g2d, "ALUOut_to_Reg", new int[]{830, 830, 500, 500}, new int[]{120, 20, 20, 100});
            drawWire(g2d, "ALUOut_to_PC", new int[]{830, 830, 70, 70}, new int[]{120, 20, 20, 100});
            
            drawWire(g2d, "B_to_Mem", new int[]{620, 620, 200, 200}, new int[]{190, 240, 240, 190}); // Store word path
        }
    }
}
