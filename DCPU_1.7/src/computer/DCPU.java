package computer;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JFrame;

/**
 * Experimental 1.7 update to Notch's 1.4 emulator
 * @author Notch, Herobrine
 *
 */
public class DCPU
{
  private static final boolean DISASSEMBLE = false;
  public char[] ram = new char[65536];
  public char pc;
  public char sp;
  public char ex;
  public char ia;
  public char[] registers = new char[8];
  public int cycles;
  public ArrayList<DCPUHardware> hardware = new ArrayList<DCPUHardware>();

  private static volatile boolean stop = false;
  private static final int khz = 100;
  private boolean isSkipping = false;
  private boolean isOnFire = false;
  private boolean queueingEnabled = false; //TODO: Verify implementation
  private char[] interrupts = new char[256];
  private int ip;
  private int iwp;

  public int getAddrB(int type)
  {
    switch (type & 0xF8) {
    case 0:
      return 65536 + (type & 0x7);
    case 8:
      return registers[type & 0x7];
    case 16:
      cycles += 1;
      return ram[pc++] + registers[type & 0x7] & 0xFFFF;
    case 24:
      switch (type & 0x7) {
      case 0:
        return (--sp) & 0xFFFF;
      case 1:
        return sp & 0xFFFF;
      case 2:
        cycles += 1;
        return ram[pc++] + sp & 0xFFFF;
      case 3:
        return 65544;
      case 4:
        return 65545;
      case 5:
        return 65552;
      case 6:
        cycles += 1;
        return ram[pc++];
      }
      cycles += 1;
      return 0x20000 | ram[pc++];
    }

    throw new IllegalStateException("Illegal a value type " + Integer.toHexString(type) + "! How did you manage that!?");
  }

  public String getStr(int type, boolean isA) {
    if (type >= 32) {
      return Integer.toHexString((type & 0x1F) + 65535 & 0xFFFF);
    }

    switch (type & 0xF8) {
    case 0:
      return ""+"ABCXYZIJ".charAt(type & 0x7);
    case 8:
      return "[" + "ABCXYZIJ".charAt(type & 0x7) + "]";
    case 16:
      return "[" + Integer.toHexString(ram[pc++]) + "+" + "ABCXYZIJ".charAt(type & 0x7) + "]";
    case 24:
      switch (type & 0x7) {
      case 0:
        return isA ? "POP" : "PUSH";
      case 1:
        return "PEEK";
      case 2:
        return "[" + Integer.toHexString(ram[pc++]) + "+SP]";
      case 3:
        return "SP";
      case 4:
        return "PC";
      case 5:
        return "EX";
      case 6:
        return "[" + Integer.toHexString(ram[pc++]) + "]";
      }
      return Integer.toHexString(ram[pc++]);
    }

    throw new IllegalStateException("Illegal value type " + Integer.toHexString(type) + "! How did you manage that!?");
  }

  public int getAddrA(int type) {
    if (type >= 32) {
      return 0x20000 | (type & 0x1F) + 65535 & 0xFFFF;
    }

    switch (type & 0xF8) {
    case 0:
      return 65536 + (type & 0x7);
    case 8:
      return registers[type & 0x7];
    case 16:
      cycles += 1;
      return ram[pc++] + registers[type & 0x7] & 0xFFFF;
    case 24:
      switch (type & 0x7) {
      case 0:
        return sp++ & 0xFFFF;
      case 1:
        return sp & 0xFFFF;
      case 2:
        cycles += 1;
        return ram[pc++] + sp & 0xFFFF;
      case 3:
        return 65544;
      case 4:
        return 65545;
      case 5:
        return 65552;
      case 6:
        cycles += 1;
        return ram[pc++];
      }
      cycles += 1;
      return 0x20000 | ram[pc++];
    }

    throw new IllegalStateException("Illegal a value type " + Integer.toHexString(type) + "! How did you manage that!?");
  }

  public char getValA(int type) {
    if (type >= 32) {
      return (char)((type & 0x1F) + 65535);
    }

    switch (type & 0xF8) {
    case 0:
      return registers[type & 0x7];
    case 8:
      return ram[registers[type & 0x7]];
    case 16:
      cycles += 1;
      return ram[ram[pc++] + registers[type & 0x7] & 0xFFFF];
    case 24:
      switch (type & 0x7) {
      case 0:
        return ram[sp++ & 0xFFFF];
      case 1:
        return ram[sp & 0xFFFF];
      case 2:
        cycles += 1;
        return ram[ram[pc++] + sp & 0xFFFF];
      case 3:
        return sp;
      case 4:
        return pc;
      case 5:
        return ex;
      case 6:
        cycles += 1;
        return ram[ram[pc++]];
      }
      cycles += 1;
      return ram[pc++];
    }

    throw new IllegalStateException("Illegal a value type " + Integer.toHexString(type) + "! How did you manage that!?");
  }

  public char get(int addr) {
    if (addr < 65536)
      return ram[addr & 0xFFFF];
    if (addr < 65544)
      return registers[addr & 0x7];
    if (addr >= 131072)
      return (char)addr;
    if (addr == 65544)
      return sp;
    if (addr == 65545)
      return pc;
    if (addr == 65552) {
      return ex;
    }
    throw new IllegalStateException("Illegal address " + Integer.toHexString(addr) + "! How did you manage that!?");
  }

  public void set(int addr, char val) {
    if (addr < 65536)
      ram[addr & 0xFFFF] = val;
    else if (addr < 65544)
      registers[addr & 0x7] = val;
    else if (addr < 131072)
    {
      if (addr == 65544)
        sp = val;
      else if (addr == 65545)
        pc = val;
      else if (addr == 65552)
        ex = val;
      else
        throw new IllegalStateException("Illegal address " + Integer.toHexString(addr) + "! How did you manage that!?"); 
    }
  }

  public static int getInstructionLength(char opcode) {
    int len = 1;
    int cmd = opcode & 0x1F;
    if (cmd == 0) {
      cmd = opcode >> 5 & 0x1F;
      if (cmd > 0) {
        int atype = opcode >> 10 & 0x3F;
        if (((atype & 0xF8) == 16) || (atype == 31) || (atype == 30)) len++; 
      }
    }
    else {
      int atype = opcode >> 5 & 0x1F;
      int btype = opcode >> 10 & 0x3F;
      if (((atype & 0xF8) == 16) || (atype == 31) || (atype == 30)) len++;
      if (((btype & 0xF8) == 16) || (btype == 31) || (btype == 30)) len++;
    }
    return len;
  }

  public void skip() {
    isSkipping = true;
  }

  public void tick() {
    cycles += 1;

    if (DISASSEMBLE)
    {
    	System.out.println((pc < 0x1000 ? "0" : "") + (pc < 0x100 ? "0" : "") + Integer.toHexString(pc) + ": " + disassemble(ram, pc));
    }
    
    if (isOnFire) {
//      cycles += 10; //Disabled to match speed of crashing seen in livestreams
      int pos = (int)(Math.random() * 65536) & 0xFFFF;
      int val = (int)(Math.random() * 65536) & 0xFFFF;
      int len = (int)(1 / (Math.random() + 0.001)) - 80;
      for (int i = 0; i < len; i++) {
        ram[pos + i & 0xFFFF] = (char)val;
      }
    }

    if (isSkipping) {
      char opcode = ram[pc];
      int cmd = opcode & 0x1F;
      pc = (char)(pc + getInstructionLength(opcode));

      if ((cmd >= 16) && (cmd <= 23))
        isSkipping = true;
      else {
        isSkipping = false;
      }

      return;
    }

    if (!queueingEnabled) {
	    if (ip != iwp) {
	      char a = interrupts[ip = ip + 1 & 0xFF];
	      if (ia > 0)
	      {
	      	queueingEnabled = true;
	        ram[--sp & 0xFFFF] = pc;
	        ram[--sp & 0xFFFF] = registers[0];
	        registers[0] = a;
	        pc = ia;
	      }	
	    }
    }

    char opcode = ram[pc++];

    int cmd = opcode & 0x1F;
    if (cmd == 0) {
      cmd = opcode >> 5 & 0x1F;
      if (cmd != 0)
      {
        int atype = opcode >> 10 & 0x3F;
        int aaddr = getAddrA(atype);
        char a = get(aaddr);

        switch (cmd) {
        case 1: //JSR
          cycles += 2;
          ram[--sp & 0xFFFF] = pc;
          pc = a;
          break;
//        case 7: //HCF
//          cycles += 8;
//          isOnFire = true;
//          break;
        case 8: //INT
          cycles += 3;
          interrupt(a);
          break;
        case 9: //IAG
          set(aaddr, ia);
          break;
        case 10: //IAS
          ia = a;
          break;
        case 11: //RFI TODO: Verify implementation
        	cycles += 2;
        	//disables interrupt queueing, pops A from the stack, then pops PC from the stack
        	queueingEnabled = false;
        	ram[++sp & 0xFFFF] = registers[0];
	        ram[++sp & 0xFFFF] = pc;
        	break;
        case 12: //IAQ TODO: Verify implementation
        	cycles += 1;
        	//if a is nonzero, interrupts will be added to the queue instead of triggered. if a is zero, interrupts will be triggered as normal again
        	if (a == 0) {
        		queueingEnabled = false;
        	} else {
        		queueingEnabled = true;
        	}
        	break;
        case 16: //HWN
          cycles += 1;
          set(aaddr, (char)hardware.size());
          break;
        case 17: //HWQ
          cycles += 3;
          if ((a < 0) || (a >= hardware.size())) break;
          ((DCPUHardware)hardware.get(a)).query();
          break;
        case 18: //HWI
          cycles += 3;
          if ((a < 0) || (a >= hardware.size())) break;
          ((DCPUHardware)hardware.get(a)).interrupt();
        case 2:
        case 3:
        case 4:
        case 5:
        case 6:
        case 13:
        case 14:
        case 15:
        default:
          break;
        }
      }
    } else {
      int atype = opcode >> 10 & 0x3F;

      char a = getValA(atype);

      int btype = opcode >> 5 & 0x1F;
      int baddr = getAddrB(btype);
      char b = get(baddr);

      switch (cmd) {
      case 1: //SET
        b = a;
        break;
      case 2:{ //ADD
        cycles += 1;
        int val = b + a;
        b = (char)val;
        ex = (char)(val >> 16);
        break;
      }case 3:{ //SUB
        cycles += 1;
        int val = b - a;
        b = (char)val;
        ex = (char)(val >> 16);
        break;
      }case 4:{ //MUL
        cycles += 1;
        int val = b * a;
        b = (char)val;
        ex = (char)(val >> 16);
        break;
      }case 5:{ //MLI
        cycles += 1;
        int val = (short)b * (short)a;
        b = (char)val;
        ex = (char)(val >> 16);
        break;
      }case 6:{ //DIV
        cycles += 2;
        if (a == 0) {
          b = ex = 0;
        } else {
          long val = (b << 16) / a;
          b = (char)(int)(val >> 16);
          ex = (char)(int)val;
        }
        break;
      }case 7:{ //DVI TODO: Ensure Rounds towards 0
        cycles += 2;
        if (a == 0) {
          b = ex = 0;
        } else {
          long val = ((short)b << 16) / (short)a;
          b = (char)(int)(val >> 16);
          ex = (char)(int)val;
        }
        break;
      }case 8: //MOD
        cycles += 2;
        if (a == 0)
          b = 0;
        else {
          b = (char)(b % a);
        }
        break;
      case 9: //MDI
      	cycles += 2;
        if (a == 0)
          b = 0;
        else {
          b = (char)((short)b % (short)a);
        }
      	break;
      case 10: //AND
        b = (char)(b & a);
        break;
      case 11: //BOR
        b = (char)(b | a);
        break;
      case 12: //XOR
        b = (char)(b ^ a);
        break;
      case 13: //SHR
        ex = (char)(b << 16 >> a);
        b = (char)(b >>> a);
        break;
      case 14: //ASR
        ex = (char)(b << 16 >>> a);
        b = (char)(b >> a);
        break;
      case 15: //SHL
        ex = (char)(b << a >> 16);
        b = (char)(b << a);
        break;
      case 16: //IFB
        cycles += 1;
        if ((b & a) == 0) skip();
        return;
      case 17: //IFC
        cycles += 1;
        if ((b & a) != 0) skip();
        return;
      case 18: //IFE
        cycles += 1;
        if (b != a) skip();
        return;
      case 19: //IFN
        cycles += 1;
        if (b == a) skip();
        return;
      case 20: //IFG
        cycles += 1;
        if (b <= a) skip();
        return;
      case 21: //IFA
        cycles += 1;
        if ((short)b <= (short)a) skip();
        return;
      case 22: //IFL
        cycles += 1;
        if (b >= a) skip();
        return;
      case 23: //IFU
        cycles += 1;
        if ((short)b >= (short)a) skip();
        return;
      case 26:{ //ADX
        cycles += 1;
        int val = b + a + ex;
        b = (char)val;
        ex = (char)(val >> 16);
        break;
      }case 27:{ //SBX TODO: Ensure returns 0x0001 if there's an overflow
        cycles += 1;
        int val = b - a + ex;
        b = (char)val;
        ex = (char)(val >> 16);
      }case 30: //STI
        b = a;
        registers[6]++;
        registers[7]++;
        break;
      case 31: //STD
      	b = a;
        registers[6]--;
        registers[7]--;
        break;
      case 24:
      case 25:
      }

      set(baddr, b);
    }
  }

  public void interrupt(char a)
  {
    interrupts[iwp = iwp + 1 & 0xFF] = a;
    if (iwp == ip) isOnFire = true; 
  }

  private String disassemble(char[] ram, char pcc)
  {
    char opc = pc;
    try {
      pc = pcc;
      char opcode = ram[pc++];
      int cmd = opcode & 0x1F;
      String str;
      if (cmd == 0) {
        cmd = opcode >> 5 & 0x1F;
        if (cmd != 0)
        {
          int atype = opcode >> 10 & 0x3F;
          str = OpCodes.special.getName(cmd) + " " + getStr(atype, true);
          return str;
        }
      } else {
        int atype = opcode >> 10 & 0x3F;
        int btype = opcode >> 5 & 0x1F;
        str = OpCodes.basic.getName(cmd) + " " + getStr(btype, false) + ", " + getStr(atype, true);
        return str;
      }
      return "!?!?!?";
    } finally {
      pc = opc;
    }//throw localObject;
  }

  private static void testCpus(int cpuCount, char[] ram) {
    DCPU[] cpus = new DCPU[cpuCount];
    for (int i = 0; i < cpuCount; i++) {
      cpus[i] = new DCPU();
      for (int j = 0; j < 65536; j++) {
        cpus[i].ram[j] = ram[j];
      }
    }

    long ops = 0L;
    int hz = 100000;
    int cyclesPerFrame = hz / 60;

    long nsPerFrame = 16666666L;
    long nextTime = System.nanoTime();

    double tick = 0;
    double total = 0;

    long startTime = System.currentTimeMillis();
    while (!stop) {
      long a = System.nanoTime();
      while (System.nanoTime() < nextTime) {
        try {
          Thread.sleep(1L);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      long b = System.nanoTime();
      for (int j = 0; j < cpuCount; j++) {
        while (cpus[j].cycles < cyclesPerFrame) {
          cpus[j].tick();
        }
        cpus[j].tickHardware();
        cpus[j].cycles -= cyclesPerFrame;
      }
      long c = System.nanoTime();
      ops += cyclesPerFrame;
      nextTime += nsPerFrame;

      tick += (c - b) / 1000000000.0;
      total += (c - a) / 1000000000.0;
    }

    long passedTime = System.currentTimeMillis() - startTime;
    System.out.println(cpuCount + " DCPU at " + ops / passedTime + " khz, " + tick * 100.0 / total + "% cpu use");
  }

  public void tickHardware() {
    for (int i = 0; i < hardware.size(); i++)
      ((DCPUHardware)hardware.get(i)).tick60hz();
  }
  
  private static String getHexRepr(int a) {
	  String str = new String(Integer.toHexString(a).toUpperCase());
	  str = new String(new char[4 - str.length()]).replace("\0", "0") + str;
	  return str;
  }

  private static void attachDisplay(final DCPU cpu)
  {
  	final VirtualClock clock = (VirtualClock) new VirtualClock().connectTo(cpu);
    final VirtualMonitor display = (VirtualMonitor)new VirtualMonitor().connectTo(cpu);
    final VirtualKeyboard keyboard = (VirtualKeyboard)new VirtualKeyboard(new AWTKeyMapping()).connectTo(cpu);
    final VirtualFloppyDrive floppyDrive = (VirtualFloppyDrive) new VirtualFloppyDrive().connectTo(cpu); 
    Thread t = new Thread() {
      public void run() {
        try {
          int SCALE = 3;
          JFrame frame = new JFrame();

          Canvas canvas = new Canvas();
          canvas.setPreferredSize(new Dimension(320 * SCALE, 128 * SCALE));
          canvas.setMinimumSize(new Dimension(320 * SCALE, 128 * SCALE));
          canvas.setMaximumSize(new Dimension(320 * SCALE, 128 * SCALE));
          canvas.setFocusable(true);
          canvas.addKeyListener(new KeyListener() {
            public void keyPressed(KeyEvent ke) {
              keyboard.keyPressed(ke.getKeyCode());
            }

            public void keyReleased(KeyEvent ke) {
              keyboard.keyReleased(ke.getKeyCode());
            }

            public void keyTyped(KeyEvent ke) {
              keyboard.keyTyped(ke.getKeyChar());
            }
          });
          frame.add(canvas);
          frame.pack();
          frame.setLocationRelativeTo(null);
          frame.setResizable(false);
          frame.setDefaultCloseOperation(3);
          frame.setVisible(true);

          BufferedImage img2 = new BufferedImage(320, 128, 2);
          BufferedImage img = new BufferedImage(128, 128, 2);
          int[] pixels = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();
          display.setPixels(pixels);

          canvas.requestFocus();
          while (true)
          {
            display.render();
            Graphics g = img2.getGraphics();
            g.setColor(new Color(pixels[12288]));
            g.fillRect(0, 0, 160, 128);
            g.drawImage(img, 16, 16, 128, 128, null);
            g.setColor(new Color(0xffffff));
            g.fillRect(160, 0, 160, 128);
            g.setColor(new Color(0));
            g.setFont(new Font("Courier",Font.PLAIN,12));
            g.drawString("A:  " + getHexRepr(cpu.registers[0]) + " [A]:  " + getHexRepr(cpu.ram[cpu.registers[0]]), 161, 10);
            g.drawString("B:  " + getHexRepr(cpu.registers[1]) + " [B]:  " + getHexRepr(cpu.ram[cpu.registers[1]]), 161, 21);
            g.drawString("C:  " + getHexRepr(cpu.registers[2]) + " [C]:  " + getHexRepr(cpu.ram[cpu.registers[2]]), 161, 32);
            g.drawString("X:  " + getHexRepr(cpu.registers[3]) + " [X]:  " + getHexRepr(cpu.ram[cpu.registers[3]]), 161, 43);
            g.drawString("Y:  " + getHexRepr(cpu.registers[4]) + " [Y]:  " + getHexRepr(cpu.ram[cpu.registers[4]]), 161, 54);
            g.drawString("Z:  " + getHexRepr(cpu.registers[5]) + " [Z]:  " + getHexRepr(cpu.ram[cpu.registers[5]]), 161, 65);
            g.drawString("I:  " + getHexRepr(cpu.registers[6]) + " [I]:  " + getHexRepr(cpu.ram[cpu.registers[6]]), 161, 76);
            g.drawString("J:  " + getHexRepr(cpu.registers[7]) + " [J]:  " + getHexRepr(cpu.ram[cpu.registers[7]]), 161, 87);
            g.drawString("PC: " + getHexRepr(cpu.pc) + " [PC]: " + getHexRepr(cpu.ram[cpu.pc]), 161, 98);
            g.drawString("SP: " + getHexRepr(cpu.sp) + " [SP]: " + getHexRepr(cpu.ram[cpu.sp]), 161, 109);
            g.drawString("IA: " + getHexRepr(cpu.ia) + "  EX : " + getHexRepr(cpu.ex), 161, 120);
            g.dispose();

            g = canvas.getGraphics();
            g.drawImage(img2, 0, 0, 320 * SCALE, 128 * SCALE, null);
            g.dispose();
            Thread.sleep(1L);
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    };
    t.start();
  }

  private static void testCpu(char[] ram)
  {
    DCPU cpu = new DCPU();
    for (int j = 0; j < 65536; j++) {
      cpu.ram[j] = ram[j];
    }
    
    attachDisplay(cpu);

    long ops = 0L;
    int hz = 100000;
    int cyclesPerFrame = hz / 60;

    long nsPerFrame = 16666666L;
    long nextTime = System.nanoTime();

    double tick = 0;
    double total = 0;

    long time = System.currentTimeMillis();
    while (!stop) {
      long a = System.nanoTime();
      while (System.nanoTime() < nextTime) {
        try {
          Thread.sleep(1L);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      long b = System.nanoTime();
      while (cpu.cycles < cyclesPerFrame) {
        cpu.tick();
      }

      cpu.tickHardware();
      cpu.cycles -= cyclesPerFrame;
      long c = System.nanoTime();
      ops += cyclesPerFrame;
      nextTime += nsPerFrame;

      tick += (c - b) / 1000000000.0;
      total += (c - a) / 1000000000.0;

      while (System.currentTimeMillis() > time) {
        time += 1000L;
        System.out.println("1 DCPU at " + ops / 1000.0 + " khz, " + tick * 100.0 / total + "% cpu use");
        tick = total = ops = 0L;
      }
    }
    cpu.dumpRegisters();
  }

  public void dumpRegisters()
  {
    System.out.print("A: " + Integer.toHexString(registers[0]) + ", ");
    System.out.print("B: " + Integer.toHexString(registers[1]) + ", ");
    System.out.println("C: " + Integer.toHexString(registers[2]));
    System.out.print("X: " + Integer.toHexString(registers[3]) + ", ");
    System.out.print("Y: " + Integer.toHexString(registers[4]) + ", ");
    System.out.println("Z: " + Integer.toHexString(registers[5]));
    System.out.print("I: " + Integer.toHexString(registers[6]) + ", ");
    System.out.println("J: " + Integer.toHexString(registers[7]));
    System.out.print("PC: " + Integer.toHexString(pc) + ", ");
    System.out.print("SP: " + Integer.toHexString(sp) + ", ");
    System.out.println("EX: " + Integer.toHexString(ex));
  }
  
  public static void load(char[] ram) throws Exception {
    DataInputStream dis = new DataInputStream(DCPU.class.getResourceAsStream("testdump.dmp"));
    try {
      for (int i = 0; ; i++)
        ram[i] = dis.readChar();
    }
    catch (IOException e) {
      dis.close();
    }
  }

  private static void dump(char[] ram, int start, int len) throws Exception {
    DataOutputStream dos = new DataOutputStream(new FileOutputStream("mem.dmp"));
    for (int i = 0; i < len; i++) {
      dos.writeChar(ram[start + i]);
    }
    dos.close();
    for (int i = 0; i < len; )
    {
      String str = Integer.toHexString(i);
      while (str.length() < 4)
        str = "0" + str;
//      System.out.print(str + ":");

      for (int j = 0; (j < 8) && (i < len); i++) {
        str = Integer.toHexString(ram[i]);
        while (str.length() < 4)
          str = "0" + str;
        System.out.print(" " + str);

        j++;
      }

      System.out.println();
    }
  }

  public static void main(String[] args) throws Exception {
    final DCPU cpu = new DCPU();
    new Assembler(cpu.ram).assemble("testfile.txt");
//    cpu.load(cpu.ram);
    
    DCPU.dump(cpu.ram, 0, 1024);
    if (args.length == 0) {
      testCpu(cpu.ram);
      return;
    }

    int threads = args.length > 0 ? Integer.parseInt(args[0]) : 1;
    final int cpusPerCore = args.length > 1 ? Integer.parseInt(args[1]) : 100;
    int seconds = args.length > 2 ? Integer.parseInt(args[2]) : 5;

    System.out.println("Aiming at 100 khz, with " + cpusPerCore + " DCPUs per thread, on " + threads + " threads.");
    System.out.println("");
    System.out.println("Running test for " + seconds + " seconds..");

    for (int i = 0; i < threads; i++) {
      new Thread() {
        public void run() {
          DCPU.testCpus(cpusPerCore, cpu.ram);
        }
      }
      .start();
    }

    for (int i = seconds; i > 0; i--) {
      System.out.println(i + "..");
      Thread.sleep(1000L);
    }
    stop = true;
  }
}