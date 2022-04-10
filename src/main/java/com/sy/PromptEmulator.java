package com.sy;

import javax.swing.*;
import java.awt.*; 
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.text.DefaultCaret;
import javax.tools.*;
import javax.tools.JavaCompiler.CompilationTask;
import java.net.*;
import java.lang.reflect.*;

public class PromptEmulator extends JPanel {
    
    public static final String PATH = new File(PromptEmulator.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getAbsolutePath();
        
    public static void main(String[] args) {
        System.out.println("Classpath: " + System.getProperty("java.class.path"));
        System.out.println("Application path: " + PATH);
        JFrame frame = new JFrame();
        frame.setTitle("Prompt Emulator");
        frame.setSize(700, 450);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        TabInstancePanel panel = new TabInstancePanel(frame);
        panel.addTabType("Console", new TabInstancePanel.PanelFactory() {
            public JPanel create() {
                return new PromptEmulator();
            }
        });
        panel.addTabType("Editor", new TabInstancePanel.PanelFactory() {
            public JPanel create() {
                return new EditorPanel();
            }
        });
        panel.addTab("Console");
        panel.addTab("Editor");
        frame.add(panel);
        frame.setVisible(true);
    }
    
    private JTextArea area = new JTextArea();
    private JTextField field = new JTextField();
    private Thread inputStreamThread = null;
    private Thread errorStreamThread = null;
    private OutputStream outputStream = null;
    private volatile boolean running = false;
    
    private File dir = new File(System.getProperty("user.dir"));
    private HashMap<String, CommandListener> commands = new HashMap<>();
    
    private Object LOCK = new Object();
    
    {
        CommandListener showDirectory = new CommandListener() {
            public void invoke(String args[]) {
                String path;
                String text = "";
                synchronized(LOCK) {
                    path = dir.getAbsolutePath();
                    for (File file : dir.listFiles()) {
                        text += file.getName() + " (file)\t";
                    }
                }
                append("\nWorking directory: " + path);
                append("\n" + text + "\n");
            }
        };
        CommandListener print = new CommandListener() {
            public void invoke(String args[]) {
                if (args.length <= 1) {
                    append("\nUsage: print [text]");
                    return;
                }
                String text = "";
                for (int t = 1; t < args.length; t++)
                    text += args[t] + " ";
                text.trim();
                if (text.startsWith("\"") && text.endsWith("\""))
                    text = text.substring(1, text.length() - 1);
                append("\n" + text);
            }
        };
        commands.put("dir", showDirectory);
        commands.put("ls", showDirectory);
        commands.put("print", print);
        commands.put("echo", print);
        commands.put("clear", new CommandListener() {
            public void invoke(String args[]) {
                area.setText("");
            }
        });
        commands.put("cd", new CommandListener() {
            public void invoke(String args[]) {
                if (args.length <= 1) {
                    append("\nUsage: cd [directory]");
                    return;
                }
                String path = "";
                for (int t = 1; t < args.length; t++)
                    path += args[t] + " ";
                path.trim();
                if (path.startsWith("\"") && path.endsWith("\""))
                    path = path.substring(1, path.length() - 1);
                boolean changed = false;
                block: synchronized(LOCK) {
                    File pathFile = new File(dir.getAbsolutePath() + File.separator + path);
                    if (pathFile.exists() && pathFile.isDirectory()) {
                        dir = pathFile;
                        changed = true;
                        break block;
                    }
                    File rootFile = new File(path);
                    if (rootFile.exists() && rootFile.isDirectory()) {
                        dir = rootFile;
                        changed = true;
                        break block;
                    }
                }
                if (changed) {
                    append(String.format("\nWorking directory changed to '%s'", dir.getPath()));
                }
                else {
                    append(String.format("\nDirectory '%s' does not exist", path));
                }
            }
        });
    }
    
    public PromptEmulator() {
        
        JTabbedPane tabs = new JTabbedPane();
        
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setBackground(Color.DARK_GRAY);
        area.setForeground(Color.GREEN);
        area.setFont(new Font(Font.MONOSPACED, 0, 12));
        field.addActionListener(new FieldListener());
        JScrollPane pane = new JScrollPane(area);
        ((DefaultCaret) area.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        this.setLayout(new BorderLayout());
        this.add(pane, BorderLayout.CENTER);
        this.add(field, BorderLayout.SOUTH);
    }
    public void append(String text) {
        synchronized(LOCK) {
            area.append(text);
        }
    } 
    private class FieldListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            handleTextEnter();
        }
    }
    
    private void handleTextEnter() {
            String text = field.getText();
            field.setText("");
            if (text.trim().isEmpty())
                return;
            synchronized(LOCK) {
                if (running) {
                    try {
                    append("\n[APP]> " + text);
                        outputStream.write(text.getBytes());
                        outputStream.flush();
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                else {
                    append("\n" + dir.getPath() + "> " + text);
                    handle(text);
                }
            }
    }
    public void waitFor() {
        try {
            if (running) {
                inputStreamThread.join();
            }
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    public void handle(String command) {
        try {
            synchronized (LOCK) {
                String[] split = command.split(" ");
                if (split.length > 0 && commands.containsKey(split[0].toLowerCase())) {
                    commands.get(split[0].toLowerCase()).invoke(split);
                    return;
                }
                Process process = Runtime.getRuntime().exec(command, null, dir);
                inputStreamThread = startParsingThread(process.getInputStream(), true);
                errorStreamThread = startParsingThread(process.getErrorStream(), false);
                outputStream = process.getOutputStream();
                running = true;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            append("\n\n" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
    private Thread startParsingThread(final InputStream stream, final boolean trigger) {
        Thread thread = new Thread(new Runnable() {
                public void run() {
                    DataInputStream input = new DataInputStream(stream);
                    try {
                        try {
                            while (true) {
                                append((char) input.readByte() + "");
                            }
                        }
                        catch (EOFException e) {
                            if (trigger)
                                System.out.println("Reached end of stream, assuming application/command exit!");
                        }
                        if (trigger) {
                            Thread.sleep(100);
                            append("\n\t\t[Application exit]");
                            running = false;
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
    private interface CommandListener {
        public void invoke(String args[]);
    }
    private static class TabInstancePanel extends JPanel {
        HashMap<String, PanelFactory> map = new HashMap<>();
        JTabbedPane pane = new JTabbedPane();
        JMenu file = new JMenu("File");
        JMenu create = new JMenu("Tab");
        public TabInstancePanel(JFrame parent) {
            this.setLayout(new BorderLayout());
            JMenuBar menubar = new JMenuBar();
            file.setMnemonic(KeyEvent.VK_F);
            file.add(createItem("Exit", KeyEvent.VK_E, "Exit Application", new ActionListener() {
                public void actionPerformed(ActionEvent event) {
                    System.exit(0);
                }
            }));
            create.add(createItem("Exit Tab", KeyEvent.VK_E, "Exit Current Tab", new ActionListener() {
                public void actionPerformed(ActionEvent event) {
                    pane.remove(pane.getSelectedIndex());
                }
            }));
            create.setMnemonic(KeyEvent.VK_T);
            menubar.add(file);
            menubar.add(create);
            parent.setJMenuBar(menubar);
            add(pane, BorderLayout.CENTER);
        }
        private JMenuItem createItem(String name, Integer key, String tooltipText, ActionListener listener) {
            JMenuItem item = new JMenuItem(name);
            if (key != null)
                item.setMnemonic(key);
            if (tooltipText != null)
                item.setToolTipText(tooltipText);
            if (listener != null)
                item.addActionListener(listener);
            return item;
            
        }
        public void addTab(String item) {
            PanelFactory factory = map.get(item);
            JPanel panel = factory.create();
            ArrayList<Integer> matches = new ArrayList<>();
            for (int t = 0; t < pane.getTabCount(); t++) {
                if (pane.getTitleAt(t).startsWith(item)) {
                    String[] split = pane.getTitleAt(t).split(" ");
                    matches.add(Integer.parseInt(split[split.length - 1].replace("(", "").replace(")", "").trim()));
                }
            }
            int i = 1;
            while (matches.contains(i))
                i++;
            pane.addTab(item + " (" + i + ")", panel);
            
        }
        private void addTabType(String name, PanelFactory factory) {
            create.add(createItem("New " + name.toLowerCase() + " tab", KeyEvent.VK_E, null, new CreateListener(name)));
            map.put(name, factory);
        }
        private class CreateListener implements ActionListener {
            String item;
            public CreateListener(String item) {
                this.item = item;
            }
            public void actionPerformed(ActionEvent e) {
                addTab(item);
            }
        }
        private interface PanelFactory {
            public JPanel create();
        }
    }
    public static class EditorPanel extends JPanel {
        
        public static final String SHELL_CLASS_START = "\npublic class Script extends promptemulator.PromptEmulator.EditorPanel.AbstractScript {\n\tpublic void run() {\n";
        public static final String SHELL_CLASS_END = "\n\t}\n}";
            
        public static final HashMap<String, ScriptShortcut> shortcuts = new HashMap<>();
        
        static {
            shortcuts.put("sleep", new ScriptShortcut() {
                public String generateCode(String[] args) {
                    int ms;
                    try {
                        ms = Integer.parseInt(args[0]);
                    }
                    catch (Exception e) {
                        return "~echo tried to sleep for a non-intergal variable! (" + args[0] + ")";
                    }
                    return String.format("try { Thread.sleep(%s); } catch (Exception exc) { exc.printStackTrace(); }", ms);
                }
            });
        }
        
        JTextArea area = new JTextArea();
        JButton run = new JButton("Run script");
        JButton help = new JButton("Help");
        JButton stop = new JButton("Force Stop");
        PromptEmulator prompt = new PromptEmulator();
        
        private Thread script = null;
        private final Object SCRIPT_LOCK = new Object();
        private volatile boolean active = false;
        
        UUID id = UUID.randomUUID();
        
        public EditorPanel() {
            
            area.setFont(new Font(Font.MONOSPACED, 1, 12));
            
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            top.add(run);
            top.add(stop);
            top.add(help);
            this.setLayout(new BorderLayout());
            this.add(top, BorderLayout.NORTH);
            JPanel editor = new JPanel(new BorderLayout());
            editor.setBorder(BorderFactory.createTitledBorder("Editor"));
            editor.add(new JScrollPane(area), BorderLayout.CENTER);
            JPanel console = new JPanel(new BorderLayout());
            console.setBorder(BorderFactory.createTitledBorder("Console"));
            console.add(prompt);
            this.add(editor, BorderLayout.CENTER);
            console.setPreferredSize(new Dimension(0, 170));
            this.add(console, BorderLayout.SOUTH);
            
            final EditorPanel panel = this;
            
            stop.addActionListener(new ActionListener() {
                @Override
                @SuppressWarnings("deprecation")
                public void actionPerformed(ActionEvent e) {
                    synchronized (SCRIPT_LOCK) {
                        script.stop();
                    }
                }
            });
            
            run.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    
                    if (active)
                        return;
                    active = true;
                    
                    final String code = SHELL_CLASS_START + area.getText() + SHELL_CLASS_END;
                    
                    synchronized (SCRIPT_LOCK) {
                        script = new Thread(new Runnable() {
                            public void run() {
                                boolean success = compile(code);
                                if (success)
                                    panel.run();
                            }
                        });
                        script.setDaemon(true);
                        script.setName("Script Thread");
                        script.start();
                        Thread waitingThread = new Thread(new Runnable() {
                            public void run() {
                                try {
                                    script.join();
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                }
                                active = false;
                            }
                        });
                        waitingThread.setDaemon(true);
                        waitingThread.setName("Script Monitor Thread");
                        waitingThread.start();
                    }
                }
            });
        }
        public void run() {
            try {
                prompt.handle("clear");
                prompt.append("Running script...\n");
                URLClassLoader classLoader = URLClassLoader.newInstance(new URL[] { new File(System.getProperty("user.dir")).toURI().toURL() });
                Class<? extends AbstractScript> type = (Class<? extends AbstractScript>) Class.forName("Script", true, classLoader);
                AbstractScript script = type.newInstance();
                Method method = AbstractScript.class.getDeclaredMethod("assignPanel", EditorPanel.class);
                method.invoke(script, this);
                method = type.getDeclaredMethod("run");
                method.invoke(script);
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }
        public String parse(String code) {
            String[] lines = code.split("\n");
            for (int t = 0; t < lines.length; t++) {
                if (lines[t].replace("\t", " ").trim().startsWith("~")) {
                    String value = lines[t].split("~")[1].trim();
                    if (value.startsWith("(") && value.endsWith(")")) {
                        String command[] = value.substring(1, value.length() - 1).split(" ");
                        if (shortcuts.containsKey(command[0])) {
                            ScriptShortcut shortcut = shortcuts.get(command[0]);
                            String args[] = new String[command.length - 1];
                            for (int i = 1; i < command.length; i++)
                                args[i - 1] = command[i];
                            lines[t] = shortcut.generateCode(args);
                        }
                    }
                    else {
                        if (value.startsWith("\"") && value.endsWith("\""))
                            value = value.substring(1, value.length() - 1);
                        lines[t] = "exec(\"" + value + "\", panel);";
                    }
                }
            }
            String parsed = "";
            for (int t = 0; t < lines.length; t++) {
                if (t == lines.length - 1) {
                    parsed += lines[t];
                }
                else {
                    parsed += lines[t] + "\n";
                }
            }
            System.out.println(parsed);
            return parsed;
        }
        public boolean compile(String code) {
            code = parse(parse(code));
            System.out.println("Compiling from thread: " + Thread.currentThread().getName());
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
            JavaFileObject file = new JavaSourceFromString("Script", code);
            Iterable<? extends JavaFileObject> compilationUnits = Arrays.asList(file);
            CompilationTask task = compiler.getTask(null, null, diagnostics,
                    Arrays.asList("-classpath", PATH), null, compilationUnits);
            boolean success = task.call();
            for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
                prompt.append("\n" + diagnostic.getCode());
                prompt.append("\n" + diagnostic.getKind().toString());
                prompt.append("\n" + diagnostic.getPosition() + "");
                prompt.append("\n" + diagnostic.getStartPosition() + "");
                prompt.append("\n" + diagnostic.getEndPosition() + "");
                prompt.append("\n" + diagnostic.getSource().toString());
                prompt.append("\n" + diagnostic.getMessage(null));
                
                System.out.println(diagnostic.getCode());
                System.out.println(diagnostic.getKind().toString());
                System.out.println(diagnostic.getPosition());
                System.out.println(diagnostic.getStartPosition());
                System.out.println(diagnostic.getEndPosition());
                System.out.println(diagnostic.getSource().toString());
                System.out.println(diagnostic.getMessage(null));
            }
            prompt.append("\nSuccess: " + success);
            return success;
        }
        public static abstract class AbstractScript {
            public EditorPanel panel = null;
            public void assignPanel(EditorPanel panel) {
                this.panel = panel;
            }
            public final void exec(String command, EditorPanel panel) {
                panel.prompt.handle(command);
                panel.prompt.waitFor();
            }
            public final void exec(String command) {
                exec(command, panel);
            }
            public final void print(Object content) {
                panel.prompt.append(content.toString());
            }
            public final void println(Object content) {
                panel.prompt.append(content.toString() + "\n");
            }
        }
        class JavaSourceFromString extends SimpleJavaFileObject {
            final String code;

            JavaSourceFromString(String name, String code) {
                super(URI.create("string:///" + name.replace('.','/') + Kind.SOURCE.extension),Kind.SOURCE);
                this.code = code;
            }

            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return code;
            }
        }
        interface ScriptShortcut {
            public String generateCode(String[] args);
        }
    }
}