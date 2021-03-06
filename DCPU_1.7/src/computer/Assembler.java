package computer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Highly experimental, incomplete 1.7 update to Notch's 1.0 assembler
 * Use at your own risk
 * @author Notch, Herobrine
 *
 */
public class Assembler
{
  public char[] ram;
  public int pc = 0;
  public Map<Position, String> labelUsages = new HashMap<Position, String>();

  public String registers = "ABCXYZIJ";
  private Map<String, Macro> macros = new HashMap<String, Macro>();
  private Macro currentMacro;
  private Scope currentScope = new Scope(null);

  public Assembler(char[] ram) {
    this.ram = ram;
  }

  
  private int decodeB(String string)
  {
  	if ((string.length() == 1) && (this.registers.indexOf(string.toUpperCase()) >= 0))
      return this.registers.indexOf(string.toUpperCase());//0x0-0x7
  	if ((string.length() == 3) && (string.startsWith("[")) && (string.endsWith("]")) && (this.registers.indexOf(string.toUpperCase().substring(1, 2)) >= 0))
      return 8 + this.registers.indexOf(string.toUpperCase().substring(1, 2));//0x8-0xf

  	if (string.toUpperCase().equals("PUSH") || string.toUpperCase().equals("[--SP]"))
      return 0x18;
    if (string.toUpperCase().equals("PEEK") || string.toUpperCase().equals("[SP]"))
      return 0x19;
    if (string.toUpperCase().equals("SP"))
      return 0x1b;
    if (string.toUpperCase().equals("PC"))
      return 0x1c;
    if (string.toUpperCase().equals("EX"))
      return 0x1d;

    
    if (Character.isDigit(string.charAt(0))) {
      int val = parseNumber(string);
      this.ram[this.pc++] = (char)val;
      return 31;
    }
    
    
    if ((string.startsWith("[")) && (string.endsWith("]")) && (Character.isDigit(string.charAt(1)))) {
      string = string.substring(1, string.length() - 1);
      if (string.indexOf("+") >= 0) {
        String[] tokens = string.split("\\+");
        int val = parseNumber(tokens[0]);
        this.ram[this.pc++] = (char)val;
        if (this.registers.indexOf(tokens[1].toUpperCase()) < 0) throw new IllegalArgumentException("Must be a register!");
        return 16 + this.registers.indexOf(tokens[1].toUpperCase());
      }
      int val = parseNumber(string);
      this.ram[this.pc++] = (char)val;
      return 30;
    }
    if ((string.startsWith("[")) && (string.endsWith("]"))) {
      string = string.substring(1, string.length() - 1);
      if (string.indexOf("+") >= 0) {
        String[] tokens = string.split("\\+");
        if ("SP".equals(tokens[0].toUpperCase())) {
        	this.ram[this.pc++] = (char)parseNumber(tokens[1]);
          return 0x1a;
        }
        else if ("SP".equals(tokens[1].toUpperCase())) {
        	this.labelUsages.put(new Position(this.currentScope, this.pc), tokens[0]);
	        this.ram[this.pc++] = 0xBEEF;
	        return 0x1a;
        }
        else if ((tokens[0].length() == 1) && (this.registers.indexOf(tokens[0].toUpperCase()) >= 0)) {
          this.ram[this.pc++] = (char)parseNumber(tokens[1]);
          if (this.registers.indexOf(tokens[0].toUpperCase()) < 0) throw new IllegalArgumentException("Must be a register!");
          return 0x10 + this.registers.indexOf(tokens[0].toUpperCase());
        } else {
	        this.labelUsages.put(new Position(this.currentScope, this.pc), tokens[0]);
	        this.ram[this.pc++] = 0xBEEF;
	        if (this.registers.indexOf(tokens[1].toUpperCase()) < 0) throw new IllegalArgumentException("Must be a register!");
	        return 0x10 + this.registers.indexOf(tokens[1].toUpperCase());
        }
      }
      this.labelUsages.put(new Position(this.currentScope, this.pc), string);
      this.ram[this.pc++] = 0xBEEF;
      return 0x1E;
    }
    this.labelUsages.put(new Position(this.currentScope, this.pc), string);
    this.ram[this.pc++] = 0xBEEF;
    return 0x1F;
  }
  
  private int decodeA(String string)
  {
  	if ((string.length() == 1) && (this.registers.indexOf(string.toUpperCase()) >= 0))
      return this.registers.indexOf(string.toUpperCase());//0x0-0x7
  	if ((string.length() == 3) && (string.startsWith("[")) && (string.endsWith("]")) && (this.registers.indexOf(string.toUpperCase().substring(1, 2)) >= 0))
      return 8 + this.registers.indexOf(string.toUpperCase().substring(1, 2));//0x8-0xf

  	if (string.toUpperCase().equals("POP") || string.toUpperCase().equals("[SP++]"))
      return 0x18;
    if (string.toUpperCase().equals("PEEK") || string.toUpperCase().equals("[SP]"))
      return 0x19;
    if (string.toUpperCase().equals("SP"))
      return 0x1b;
    if (string.toUpperCase().equals("PC"))
      return 0x1c;
    if (string.toUpperCase().equals("EX"))
      return 0x1d;
    
    if (Character.isDigit(string.charAt(0)) || "-1".equals(string)) {
      int val = parseNumber(string);
      if (val < 31 || val == 0xFFFF) {
        return 32 + ((val + 1) % 0x10000);
      }
      this.ram[this.pc++] = (char)val;
      return 31;
    }
    
    if ((string.startsWith("[")) && (string.endsWith("]")) && (Character.isDigit(string.charAt(1)))) {
      string = string.substring(1, string.length() - 1);
      if (string.indexOf("+") >= 0) {
        String[] tokens = string.split("\\+");
        int val = parseNumber(tokens[0]);
        this.ram[this.pc++] = (char)val;
        if (this.registers.indexOf(tokens[1].toUpperCase()) < 0) throw new IllegalArgumentException("Must be a register!");
        return 16 + this.registers.indexOf(tokens[1].toUpperCase());
      }
      int val = parseNumber(string);
      this.ram[this.pc++] = (char)val;
      return 30;
    }
    if ((string.startsWith("[")) && (string.endsWith("]"))) {
      string = string.substring(1, string.length() - 1);
      if (string.indexOf("+") >= 0) {
        String[] tokens = string.split("\\+");
        if ("SP".equals(tokens[0].toUpperCase())) {
        	this.ram[this.pc++] = (char)parseNumber(tokens[1]);
          return 0x1a;
        }
        else if ("SP".equals(tokens[1].toUpperCase())) {
        	this.labelUsages.put(new Position(this.currentScope, this.pc), tokens[0]);
	        this.ram[this.pc++] = 0xBEEF;
	        return 0x1a;
        }
        else if ((tokens[0].length() == 1) && (this.registers.indexOf(tokens[0].toUpperCase()) >= 0)) {
        	this.ram[this.pc++] = (char)parseNumber(tokens[1]);
          if (this.registers.indexOf(tokens[0].toUpperCase()) < 0) throw new IllegalArgumentException("Must be a register!");
          return 0x10 + this.registers.indexOf(tokens[0].toUpperCase());
        } else {
	        this.labelUsages.put(new Position(this.currentScope, this.pc), tokens[0]);
	        this.ram[this.pc++] = 0xBEEF;
	        if (this.registers.indexOf(tokens[1].toUpperCase()) < 0) throw new IllegalArgumentException("Must be a register!");
	        return 0x10 + this.registers.indexOf(tokens[1].toUpperCase());
        }
      }
      this.labelUsages.put(new Position(this.currentScope, this.pc), string);
      this.ram[this.pc++] = 0xBEEF;
      return 0x1E;
    }
    this.labelUsages.put(new Position(this.currentScope, this.pc), string);
    this.ram[this.pc++] = 0xBEEF;
    return 0x1F;
  }

  private int parseNumber(String string)
  {
    int val = 0;
    if (string.startsWith("0x")) val = Integer.parseInt(string.substring(2), 16);
    else if (string.startsWith("0b")) val = Integer.parseInt(string.substring(2), 2);
    else val = Integer.parseInt(string);
    val &= 0xFFFF;
    return val;
  }

  private void parseData(String string) {
    if (Character.isDigit(string.charAt(0))) {
      this.ram[this.pc++] = (char)parseNumber(string);
    } else if (string.startsWith("\"")) {
      string = string.substring(1, string.length() - 1);
      for (int i = 0; i < string.length(); i++)
        this.ram[this.pc++] = string.charAt(i);
    } else {
      this.labelUsages.put(new Position(this.currentScope, this.pc), string);
      this.ram[this.pc++] = 48879;
    }
  }

  private void parseLine(String line) {
    if (line.length() == 0) return;
    String[] words = line.split("\\\"");
    line = "";
    for (int i = 0; i < words.length; i++) {
      if (i % 2 == 0) words[i] = words[i].replaceAll("\\{", " \\{ ");
      if (i % 2 == 0) words[i] = words[i].replaceAll("\\}", " \\} ");
      line = line + words[i];
      if (i >= words.length - 1) continue; line = line + "\"";
    }

    List<String> tokenList = new ArrayList<String>();
    line = line.replace(";", " ;");//TODO FIXME XXX Temp hack
    String delims = " \t,";//"\t,";
    StringTokenizer st = new StringTokenizer(line, delims + "\"[", true);
    while (st.hasMoreTokens()) {
      String token = st.nextToken(delims + "\"[");
      if (token.equalsIgnoreCase("\"")) {
        token = "\"" + st.nextToken("\"") + "\"";
        if (st.hasMoreTokens()) st.nextToken("\"");
      }
      if (token.equalsIgnoreCase("[")) {
        token = "[" + st.nextToken("]").replaceAll(" ", "") + "]";
        if (st.hasMoreTokens()) st.nextToken("]");
      }
      if (token.equalsIgnoreCase("PICK")) {
      	st.nextToken();
        token = "[SP+" + st.nextToken() + "]";
      }
      if ((token.length() > 1) || (delims.indexOf(token) < 0)) {
        tokenList.add(token);
      }
    }
    String[] tokens = (String[])tokenList.toArray(new String[0]);

    handleTokens(tokens);
  }

  public void handleTokens(String[] tokens)
  {
    for (int i = 0; i < tokens.length; i++) {
      if (tokens[i].startsWith(";")) {
        return;
      }
      if (this.currentMacro != null) {
        this.currentMacro.tokens.add(tokens[i]);
        if (tokens[i].equals("}")) {
          this.currentMacro = null;
        }
      }
      else if (tokens[i].startsWith(":")) {
        this.currentScope.labelPositions.put(tokens[i].substring(1), new Position(this.currentScope, this.pc));
      } else if (tokens[i].endsWith(":")) {
        this.currentScope.labelPositions.put(tokens[i].substring(0, tokens[i].length()-1), new Position(this.currentScope, this.pc));
      } else if (tokens[i].equalsIgnoreCase("#macro")) {
        String title = "";
        while (!tokens[i + 1].equals("{")) {
          i++; String t = tokens[i];
          if (title.length() > 0) title = title + ",";
          title = title + t;
        }
        String[] parts = title.split("[,) \t]+");
        this.currentMacro = new Macro();
        for (int j = 1; j < parts.length; j++) {
          this.currentMacro.params.add(parts[j]);
        }
        this.macros.put(parts[0] + "(" + parts.length, this.currentMacro);
      } else if (tokens[i].equalsIgnoreCase("DAT")) {
        i++;
        for (; i < tokens.length; i++) {
          if (tokens[i].startsWith(";")) {
            return;
          }
          parseData(tokens[i]);
        }
      }
      else if (OpCodes.basic.contains(tokens[i].toUpperCase())) {
      	//aaaaaabbbbbooooo 
      	int op = this.pc;
        this.ram[this.pc++] = 0;
        int opCode = OpCodes.basic.getId(tokens[i].toUpperCase());
        i++; int b = decodeB(tokens[i]);
        i++; int a = decodeA(tokens[i]);
//        if ((b >= 31) && (opCode < 12)) {
//          throw new IllegalArgumentException("Can't assign a literal value!");
//        }
        this.ram[op] = (char)(opCode | b << 5 | a << 10);
      } else if (OpCodes.special.contains(tokens[i].toUpperCase())) {
      	//aaaaaaooooo00000
        int op = this.pc;
        this.ram[this.pc++] = 0;
        int opCode = OpCodes.special.getId(tokens[i].toUpperCase());
        i++; int a = decodeA(tokens[i]);
        this.ram[op] = (char)(opCode << 5 | a << 10);
      } else if (tokens[i].equals("{")) {
        this.currentScope = new Scope(this.currentScope);
      } else if (tokens[i].equals("}")) {
        this.currentScope = this.currentScope.parent;
      } else if (tokens[i].contains("("))
      {
        String title = "";
        do
        {
          String t = tokens[i];
          if (title.length() > 0) title = title + ",";
          title = title + t;
        }
        while (!
          tokens[i++].endsWith(")"));
        i--;
        String[] parts = title.split("[,) \t]+");
        ((Macro)this.macros.get(parts[0] + "(" + parts.length)).insert(parts);
      } else {
        throw new IllegalArgumentException("Bad token " + tokens[i]);
      }
    }
  }

  public void include(String file)
    throws Exception
  {
    Scope oldScope = this.currentScope;
    this.currentScope = new Scope(oldScope);
    oldScope.inheritedScopes.add(this.currentScope);
    String fileName = file;
    BufferedReader br = new BufferedReader(new InputStreamReader(Assembler.class.getResourceAsStream("/" + file)));
    String line = "";
    int lines = 0;
    while ((line = br.readLine()) != null) {
      lines++;
      line = line.trim();
      if (line.startsWith("#include "))
        try {
          include(line.substring("#include ".length()));
        } catch (Exception e) {
          System.out.println("[" + fileName + ":" + lines + "] Failed to include file: " + line.trim());
          e.printStackTrace();
        }
      else {
        try {
          parseLine(line);
        } catch (Exception e) {
          System.out.println("[" + fileName + ":" + lines + "] Failed to parse line: " + line.trim());
          e.printStackTrace();
        }
      }
    }
    br.close();
    this.currentScope = oldScope;
  }

  public void assemble(String file) throws Exception
  {
    include(file);

    for (Position pos : this.labelUsages.keySet()) {
      String label = (String)this.labelUsages.get(pos);
      if (label.startsWith("PC+")) {
        int toSkip = Integer.parseInt(label.substring(3));
        int pp = pos.pos - 1;
        for (int i = 0; i <= toSkip; i++) {
          pp += DCPU.getInstructionLength(this.ram[pp]);
        }
        this.ram[pos.pos] = (char)pp;
      } else {
        Position labelPos = pos.scope.findLabel(label);
        if (labelPos == null) {
          throw new IllegalArgumentException("Undefined label " + label);
        }
        this.ram[pos.pos] = (char)labelPos.pos;
      }
    }
  }

  private class Macro
  {
    public List<String> tokens = new ArrayList<String>();
    public List<String> params = new ArrayList<String>();

    private Macro() {  }

    public void insert(String[] values) { String[] ts = new String[this.tokens.size()];
      for (int i = 0; i < ts.length; i++) {
        String token = (String)this.tokens.get(i);
        int p = this.params.indexOf(token);
        if (p >= 0)
          ts[i] = values[p + 1];
        else {
          ts[i] = token;
        }
      }
      Assembler.this.handleTokens(ts);
    }
  }

  private class Position
  {
    public final Assembler.Scope scope;
    public final int pos;

    public Position(Assembler.Scope scope, int pos)
    {
      this.scope = scope;
      this.pos = pos;
    }
  }

  private class Scope
  {
    public Map<String, Assembler.Position> labelPositions = new HashMap<String, Assembler.Position>();
    public Scope parent;
    public List<Scope> inheritedScopes = new ArrayList<Scope>();

    public Scope(Scope parent) {
      this.parent = parent;
    }

    public Assembler.Position findLabel(String label) {
      return findLabel(label, new ArrayList<Scope>());
    }

    public Assembler.Position findLabel(String label, List<Scope> testedScopes) {
      if (testedScopes.contains(this)) return null;
      testedScopes.add(this);
      if (this.labelPositions.containsKey(label)) return (Assembler.Position)this.labelPositions.get(label);
      for (int i = 0; i < this.inheritedScopes.size(); i++) {
        Assembler.Position p = ((Scope)this.inheritedScopes.get(i)).findLabel(label, testedScopes);
        if (p != null) return p;
      }
      if (this.parent != null) return this.parent.findLabel(label, testedScopes);
      return null;
    }
  }
  
  public static void main(String[] args) {
  	Assembler assembler = new Assembler(new char[65536]);
  	try {
			assembler.assemble("testfile.txt");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}