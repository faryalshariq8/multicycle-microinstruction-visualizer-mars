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
    private static String version = " Version 2.0";

    private JPanel mainPanel;
    private JLabel stateLabel;
    private JTextArea activeSignalsArea;
    private JTextArea internalRegistersArea;
    private JButton microStepButton;
    private DatapathPanel datapathPanel;

    private int currentState = 0; // 0: Fetch, 1: Decode, 2: Execute, 3: Memory, 4: WriteBack
    private int maxState = 4; // Depends on instruction
    private int currentAddress = -1;
    
    // Thread-safe cached data
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
                // Read everything on Simulator thread to prevent deadlocks with the GUI
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
            datapathPanel.setActiveBlocks(new String[]{}); // Clear highlights
            datapathPanel.repaint();
        } else {
            updateVisualsForState();
        }
    }

    private void updateVisualsForState() {
        // Decode Opcode
        int opcode = 0;
        if (currentInstructionBinary != null && currentInstructionBinary.length() == 32) {
            opcode = Integer.parseInt(currentInstructionBinary.substring(0, 6), 2);
        }

        switch(currentState) {
            case 0:
                maxState = 4; // default, will adjust based on opcode
                stateLabel.setText("Current State: Cycle 1 (Fetch) - " + currentMnemonic);
                activeSignalsArea.setText("Active Control Signals:\nMemRead=1, IorD=0, IRWrite=1, PCWrite=1\n\nMicro-ops:\nMAR <- PC; Read Memory; IR <- MDR; PC <- PC + 4");
                internalRegistersArea.setText("PC: " + savedNextPC + "\nIR: " + currentInstructionBinary + "\nMAR: " + savedHexPC + "\nMDR: " + currentInstructionBinary);
                datapathPanel.setActiveBlocks(new String[]{"PC", "Memory", "IR"});
                break;
            case 1:
                stateLabel.setText("Current State: Cycle 2 (Decode) - " + currentMnemonic);
                activeSignalsArea.setText("Active Control Signals:\nALUSrcA=0, ALUSrcB=11\n\nMicro-ops:\nA <- Reg[rs]; B <- Reg[rt]; ALUOut <- PC + (sign-ext(imm) << 2)");
                internalRegistersArea.setText("PC: " + savedNextPC + "\nIR: " + currentInstructionBinary + "\nA: Reg[rs]\nB: Reg[rt]");
                datapathPanel.setActiveBlocks(new String[]{"RegFile", "A", "B", "ALUOut"});
                break;
            case 2:
                stateLabel.setText("Current State: Cycle 3 (Execute) - " + currentMnemonic);
                if (opcode == 0) { // R-type
                    activeSignalsArea.setText("Active Control Signals:\nALUSrcA=1, ALUSrcB=00, ALUOp=10\n\nMicro-ops:\nALUOut <- A op B");
                    datapathPanel.setActiveBlocks(new String[]{"A", "B", "ALU", "ALUOut"});
                    maxState = 3; // R-type finishes in Cycle 4 (WriteBack)
                } else if (opcode == 35 || opcode == 43) { // lw or sw
                    activeSignalsArea.setText("Active Control Signals:\nALUSrcA=1, ALUSrcB=10, ALUOp=00\n\nMicro-ops:\nALUOut <- A + sign-ext(imm)");
                    datapathPanel.setActiveBlocks(new String[]{"A", "ALU", "ALUOut"});
                    if (opcode == 43) maxState = 3; // SW finishes in Cycle 4
                } else if (opcode == 4) { // beq
                    activeSignalsArea.setText("Active Control Signals:\nALUSrcA=1, ALUSrcB=00, ALUOp=01, PCWriteCond=1\n\nMicro-ops:\nif (A == B) PC <- ALUOut");
                    datapathPanel.setActiveBlocks(new String[]{"A", "B", "ALU", "PC"});
                    maxState = 2; // BEQ finishes here
                }
                internalRegistersArea.setText("PC: " + savedNextPC + "\nIR: " + currentInstructionBinary + "\nALUOut: Computed Address/Value");
                break;
            case 3:
                if (opcode == 0) { // R-type WriteBack (actually cycle 4)
                    stateLabel.setText("Current State: Cycle 4 (WriteBack) - " + currentMnemonic);
                    activeSignalsArea.setText("Active Control Signals:\nRegDst=1, RegWrite=1, MemtoReg=0\n\nMicro-ops:\nReg[rd] <- ALUOut");
                    datapathPanel.setActiveBlocks(new String[]{"ALUOut", "RegFile"});
                } else if (opcode == 35) { // lw
                    stateLabel.setText("Current State: Cycle 4 (Memory Access) - " + currentMnemonic);
                    activeSignalsArea.setText("Active Control Signals:\nMemRead=1, IorD=1\n\nMicro-ops:\nMDR <- Memory[ALUOut]");
                    datapathPanel.setActiveBlocks(new String[]{"ALUOut", "Memory", "MDR"});
                } else if (opcode == 43) { // sw
                    stateLabel.setText("Current State: Cycle 4 (Memory Access) - " + currentMnemonic);
                    activeSignalsArea.setText("Active Control Signals:\nMemWrite=1, IorD=1\n\nMicro-ops:\nMemory[ALUOut] <- B");
                    datapathPanel.setActiveBlocks(new String[]{"ALUOut", "B", "Memory"});
                }
                break;
            case 4:
                if (opcode == 35) { // lw WriteBack
                    stateLabel.setText("Current State: Cycle 5 (WriteBack) - " + currentMnemonic);
                    activeSignalsArea.setText("Active Control Signals:\nRegDst=0, RegWrite=1, MemtoReg=1\n\nMicro-ops:\nReg[rt] <- MDR");
                    datapathPanel.setActiveBlocks(new String[]{"MDR", "RegFile"});
                }
                break;
        }
        datapathPanel.repaint();
    }

    // Custom JPanel for rendering the Datapath block diagram
    class DatapathPanel extends JPanel {
        private String[] activeBlocks = new String[]{};

        public void setActiveBlocks(String[] blocks) {
            this.activeBlocks = blocks;
        }

        private boolean isActive(String name) {
            for (String b : activeBlocks) {
                if (b.equals(name)) return true;
            }
            return false;
        }

        private void drawBlock(Graphics g, String name, int x, int y, int w, int h) {
            if (isActive(name)) {
                g.setColor(new Color(255, 255, 153)); // Highlight Yellow
                g.fillRect(x, y, w, h);
                g.setColor(Color.RED);
                ((Graphics2D)g).setStroke(new BasicStroke(2));
                g.drawRect(x, y, w, h);
                ((Graphics2D)g).setStroke(new BasicStroke(1));
            } else {
                g.setColor(new Color(220, 220, 220)); // Gray
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

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw datapath blocks
            drawBlock(g, "PC", 50, 100, 40, 60);
            drawBlock(g, "Memory", 150, 70, 100, 120);
            drawBlock(g, "IR", 300, 50, 80, 50);
            drawBlock(g, "MDR", 300, 140, 80, 50);
            drawBlock(g, "RegFile", 450, 100, 100, 120);
            drawBlock(g, "A", 600, 70, 40, 40);
            drawBlock(g, "B", 600, 150, 40, 40);
            drawBlock(g, "ALU", 700, 100, 60, 80);
            drawBlock(g, "ALUOut", 800, 120, 60, 40);

            // Draw conceptual connecting lines
            g.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(1));
            
            // PC to Mem
            g.drawLine(90, 130, 150, 130);
            // Mem to IR & MDR
            g.drawLine(250, 130, 275, 130);
            g.drawLine(275, 75, 300, 75);
            g.drawLine(275, 165, 300, 165);
            g.drawLine(275, 75, 275, 165);
            // IR to RegFile
            g.drawLine(380, 75, 450, 110);
            // MDR to RegFile
            g.drawLine(380, 165, 450, 165);
            // RegFile to A and B
            g.drawLine(550, 130, 575, 130);
            g.drawLine(575, 90, 600, 90);
            g.drawLine(575, 170, 600, 170);
            g.drawLine(575, 90, 575, 170);
            // A and B to ALU
            g.drawLine(640, 90, 700, 110);
            g.drawLine(640, 170, 700, 170);
            // ALU to ALUOut
            g.drawLine(760, 140, 800, 140);
            // Loopback ALUOut to PC/Mem/RegFile (conceptual single line back)
            g.drawLine(830, 120, 830, 20);
            g.drawLine(830, 20, 70, 20);
            g.drawLine(70, 20, 70, 100);
            g.drawLine(200, 20, 200, 70); // To memory
            g.drawLine(500, 20, 500, 100); // To RegFile
        }
    }
}
