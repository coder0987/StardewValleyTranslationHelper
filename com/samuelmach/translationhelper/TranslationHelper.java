package com.samuelmach.translationhelper;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.*;
import com.formdev.flatlaf.*;

import static com.samuelmach.translationhelper.TranslationHelper.Q;

public class TranslationHelper {
    private static MainFrame frame;
    public static String filepath;
    public static String project;
    public static String modId;
    public static String languageCode;
    private static TextField fileChooser;
    public static final char Q = '\"';

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel( new FlatLightLaf() );
        } catch( Exception ex ) {
            System.err.println( "Failed to initialize LaF" );
        }

        frame = new MainFrame("Stardew Valley Translation Tool");

        if (Files.exists(Path.of("TranslationHelper.properties"))) {
            begin();
        } else {
            initFirstMenu();
        }

    }
    public static void begin() {
        BufferedReader reader;
        String projects = "";
        try {
            reader = new BufferedReader(new FileReader("TranslationHelper.properties"));
            String line = reader.readLine();

            String[] arr = line.split(": ");
            filepath = arr[1];
            for (int i=2; i<arr.length; i++) {
                filepath += ": " + arr[i];
            }

            String[] projectsArr = reader.readLine().split(": ");
            if (projectsArr.length > 1)
                projects = projectsArr[1];

            reader.close();
        } catch (Exception e) {
            System.out.println("Error reading config file");
            e.printStackTrace();
            return;
        }
        Label header = new Label("Install location located: " + filepath);
        frame.add(header);

        String[] projectsArr = projects.split(",");
        for (String s : projectsArr) {
            if (!s.isEmpty()) {
                CustomButton cb = new CustomButton(s, TranslationHelper::chooseProject);
                cb.setActionCommand(s);
                frame.add(cb);
            }
        }

        CustomButton cb = new CustomButton("New Project", TranslationHelper::newProject);
        frame.add(cb);
        frame.revalidate();
        frame.repaint();
    }
    static void chooseProject(ActionEvent e) {
        project = e.getActionCommand();
        frame.getContentPane().removeAll();
        frame.add(new Label("Opening project " + project));
        frame.revalidate();
        frame.repaint();

        if (Files.notExists(Paths.get(TranslationHelper.filepath, "Mods", project))) {
            if (!project.equals(project.trim())) {
                project = project.trim();
            } else {
                frame.add(new Label("Project " + project + " does not exist!"));
                CustomButton cb = new CustomButton("Back to Project List", (ActionEvent ae) -> begin());
                frame.add(cb);
                frame.revalidate();
                frame.repaint();
                return;
            }
        }

        beginProject();
    }
    static void beginProject() {
        /* Assuming:
        * SMAPI, Content Patcher, and XNB Hack are installed
        * Mod folder is made
        * content.json and manifest.json are initialized
        * assets folder is made
        * */
        final String MOD_PATH = Paths.get(filepath,"Mods",project).toString();

        String contentLines = readFile(Paths.get(MOD_PATH,"content.json").toString());

        JSONObject contentJSON = new JSONObject(contentLines);
        JSONObject manifestJSON = new JSONObject(readFile(Paths.get(MOD_PATH,"manifest.json").toString()));
        modId = manifestJSON.getString("UniqueID");

        JSONObject search = contentJSON.getJSONArray("Changes").getJSONObject(0);
        for (Object j : contentJSON.getJSONArray("Changes")) {
            if (((JSONObject) j).getString("Target").equals("Data/AdditionalLanguages")) {
                search = (JSONObject) j;
            }
        }

        JSONObject entries = search.getJSONObject("Entries").getJSONObject(modId);
        //"Entries":{"YourNameNoSpaces.LanguageName":{"LanguageCode":"la","TimeFormat":"[HOURS_24_00]:[MINUTES]","UseLatinFont":true,"ClockDateFormat":"[DAY_OF_WEEK] [DAY_OF_MONTH]","ID":"YourNameNoSpaces.LanguageName","ButtonTexture":"Mods/YourNameNoSpaces.LanguageName/Button","ClockTimeFormat":"[HOURS_24_00]:[MINUTES]"}

        frame.add(new Label("Language Code"));
        TextField lc = new TextField(entries.getString("LanguageCode"));
        languageCode = lc.getText();
        frame.add(lc);
        frame.add(new Label("Time Format"));
        TextField timeFormat = new TextField(entries.getString("TimeFormat"));
        frame.add(timeFormat);
        frame.add(new Label("Use Latin Font"));
        TextField useLatinFont = new TextField(String.valueOf(entries.getBoolean("UseLatinFont")));
        frame.add(useLatinFont);
        frame.add(new Label("Clock Date Format"));
        TextField clockDateFormat = new TextField(entries.getString("ClockDateFormat"));
        frame.add(clockDateFormat);
        frame.add(new Label("Clock Time Format"));
        TextField clockTimeFormat = new TextField(entries.getString("ClockTimeFormat"));
        frame.add(clockTimeFormat);

        frame.add(new CustomButton("Save", (e) -> {
            entries.put("LanguageCode",lc.getText());
            languageCode = lc.getText();
            entries.put("TimeFormat",timeFormat.getText());
            entries.put("UseLatinFont",Boolean.parseBoolean(useLatinFont.getText()));
            entries.put("ClockDateFormat",clockDateFormat.getText());
            entries.put("ClockTimeFormat",clockTimeFormat.getText());
            saveFile(Paths.get(MOD_PATH,"content.json").toString(),contentJSON);
        }));
        frame.add(new CustomButton("Continue", (e) -> {
            frame.getContentPane().removeAll();
            frame.add(new Label("Loading JSON files..."));
            frame.revalidate();
            frame.repaint();
            loadFilesToTranslate();
        }));
        frame.revalidate();
        frame.repaint();
    }
    static void loadFilesToTranslate() {
        final String MOD_PATH = Paths.get(filepath,"Mods",project).toString();
        final String LANGUAGE_CODE = languageCode.toLowerCase() + "-" + languageCode.toUpperCase();
        String progress = "";
        try {
            progress = readFile(project + "_progress.json");
        } catch (RuntimeException e) {
            System.out.println(project + "_progress.json does not exist.");
            frame.getContentPane().removeAll();
            frame.add(new Label("Blank project. Copying data files from Stardew Valley."));
            frame.revalidate();
            frame.repaint();
        }
        JSONObject progressJSON = progress.isEmpty() ? new JSONObject() : new JSONObject(progress);
        String contentPath = Paths.get(MOD_PATH, "content.json").toString();
        JSONObject contentJSON = new JSONObject(readFile(contentPath));
        JSONArray changes = contentJSON.getJSONArray("Changes");

        Path assetsPath = Paths.get(MOD_PATH, "assets");
        if (progress.isEmpty()) {
            System.out.println("Copying JSON files...");
            //Load all JSON files from Stardew Valley into the mod
            File baseDir = new File(Paths.get(filepath,"Content (unpacked)").toUri());
            File assetsDir = new File(assetsPath.toUri());
            //go through each subdirectory and find all files that have translations
            //For each file with a translation, create a copy of it in assets and add it to content.json

            if (baseDir.listFiles() == null) {
                System.out.println(baseDir.getAbsolutePath());
                frame.getContentPane().removeAll();
                frame.add(new Label("There was an error loading the Content Files from " + Paths.get(filepath,"Content (unpacked)")));
                frame.revalidate();
                frame.repaint();
                return;
            }

            progressJSON.put("button.png","loaded");

            int numPeriodsInPath = MOD_PATH.split("\\.").length - 1;
            String correctedPath = "";
            for (int i=0; i<numPeriodsInPath; i++) {
                correctedPath += MOD_PATH.split("\\.")[i] + ".";
            }

            for (File f: Objects.requireNonNull(baseDir.listFiles())) {
                if (f.isDirectory() && !f.getName().equals("Fonts")) {
                    JSONObject progressBaseDir = new JSONObject();
                    progressJSON.put(f.getName(), progressBaseDir);
                    File comparableDir = new File(Paths.get(assetsDir.getAbsolutePath(),f.getName()).toUri());
                    for (File sub: Objects.requireNonNull(f.listFiles())) {
                        //Some two-layer-deep dirs, but no three layer
                        if (sub.isDirectory()) {
                            JSONObject progressSubDir = new JSONObject();
                            progressBaseDir.put(sub.getName(),progressSubDir);
                            for (File doubleSub : Objects.requireNonNull(sub.listFiles())) {
                                File deepDir = new File(Paths.get(assetsDir.getAbsolutePath(),f.getName(),sub.getName()).toUri());
                                if (doubleSub.getName().contains("de-DE")) {
                                    final String fileName = doubleSub.getName().split("\\.")[0] + "." + LANGUAGE_CODE + "." + doubleSub.getName().split("\\.")[2];
                                    //Requires translation
                                    if (!deepDir.exists()) {
                                        boolean success = deepDir.mkdirs();
                                    }
                                    boolean alreadyThere = copyFileUnlessExists(correctedPath + doubleSub.getAbsolutePath().split("\\.")[numPeriodsInPath] + "." + doubleSub.getAbsolutePath().split("\\.")[numPeriodsInPath + 2], Paths.get(deepDir.getAbsolutePath(),fileName).toString());

                                    progressSubDir.put(fileName, alreadyThere ? "loaded" : "complete");
                                    if (alreadyThere) {
                                        JSONObject change = new JSONObject();
                                        change.put("Action", "Load");
                                        change.put("Target", f.getName() + "/" + sub.getName() + "/" + doubleSub.getName().split("\\.")[0]);
                                        change.put("FromFile", "assets/" + f.getName() + "/" + sub.getName() + "/" + fileName);
                                        JSONObject when = new JSONObject();
                                        when.put("Language", languageCode);
                                        change.put("when", when);
                                        changes.put(change);
                                    }
                                }
                            }
                        } else if (sub.getName().contains("de-DE")) {
                            //Requires translation
                            final String fileName = sub.getName().split("\\.")[0] + "." + LANGUAGE_CODE + "." + sub.getName().split("\\.")[2];
                            if (!comparableDir.exists()) {
                                boolean success = comparableDir.mkdir();
                            }
                            boolean alreadyThere = copyFileUnlessExists(correctedPath + sub.getAbsolutePath().split("\\.")[numPeriodsInPath] + "." + sub.getAbsolutePath().split("\\.")[numPeriodsInPath + 2], Paths.get(comparableDir.getAbsolutePath(),fileName).toString());
                            progressBaseDir.put(fileName, alreadyThere ? "loaded" : "complete");
                            if (alreadyThere) {
                                JSONObject change = new JSONObject();
                                change.put("Action", "Load");
                                change.put("Target", f.getName() + "/" + sub.getName().split("\\.")[0]);
                                change.put("FromFile", "assets/" + f.getName() + "/" + fileName);
                                JSONObject when = new JSONObject();
                                when.put("Language", languageCode);
                                change.put("when", when);
                                changes.put(change);
                            }
                        }
                    }
                }
            }
            saveFile(contentPath,contentJSON);
            saveFile(project + "_progress.json",progressJSON);

            frame.getContentPane().removeAll();
            frame.add(new Label("Files copied successfully"));
            System.out.println("Copied files!");
        } else {
            System.out.println("Loading progress...");
            frame.getContentPane().removeAll();
            frame.add(new Label("Progress loaded successfully"));
        }
        frame.revalidate();
        frame.repaint();

        //JSON is now loaded. Name_progress.json is loaded. content.json is updated.
        renderDir(assetsPath.toString(), progressJSON);
    }
    static void renderDir(String dirname, JSONObject progress) {
        String png = ".png";

        frame.getContentPane().removeAll();
        frame.setLayout(new BorderLayout());
        GridBagLayout grid = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();

        c.fill = GridBagConstraints.BOTH;
        c.gridwidth = 1;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0.5;
        c.weighty = 0.5;

        JPanel p = new JPanel(grid);

        JScrollPane scrPane = new JScrollPane(p);
        scrPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrPane.setPreferredSize(frame.getPreferredSize());
        frame.add(scrPane, BorderLayout.CENTER);


        if (dirname.split("assets").length > 1) {
            //Subdir
            CustomButton cb = new CustomButton("Back", (e) -> {
                renderDir(e.getActionCommand(), progress);
            });
            cb.setActionCommand(Path.of(dirname).getParent().toString());
            p.add(cb,c);
            c.gridx = 1;
            p.add(new Label(""),c);
            c.gridx = 0;
            c.gridy++;
        }

        File currentDir = new File(dirname);

        for (File f: Objects.requireNonNull(currentDir.listFiles())) {
            if (f.isDirectory()) {
                CustomButton cb = new CustomButton(f.getName(), (e) -> {
                    renderDir(e.getActionCommand(), progress);
                });
                cb.setActionCommand(f.getAbsolutePath());
                p.add(cb,c);
                c.gridx = 1;
                p.add(new Label("View Directory"),c);
                c.gridx = 0;
                c.gridy++;
            } else {
                //File
                String status = getProgressOf(f.getAbsolutePath(), progress);
                if (f.getName().contains(png)) {
                    p.add(new Label(f.getName() + ": " + status), c);
                    c.gridx = 1;
                    if (status.equals("loaded")) {
                        CustomButton cb = new CustomButton("Mark Complete", (e) -> {
                            setProgressOf(f.getAbsolutePath(),progress,"complete");
                        });
                        p.add(cb,c);
                    } else {
                        p.add(new Label(""),c);
                    }
                } else {
                    CustomButton cb = new CustomButton(f.getName(), (e) -> {
                        editfile(e.getActionCommand(), progress);
                    });
                    cb.setActionCommand(f.getAbsolutePath());
                    p.add(cb,c);
                    c.gridx = 1;
                    p.add(new Label(status),c);
                }
                c.gridx = 0;
                c.gridy++;
            }
        }
        scrPane.revalidate();
        scrPane.repaint();
        frame.revalidate();
        frame.pack();
        frame.repaint();
    }
    static void editfile(String filename, JSONObject progress) {
        frame.getContentPane().removeAll();
        frame.setLayout(new BorderLayout());
        GridBagLayout grid = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();

        c.fill = GridBagConstraints.BOTH;
        c.gridwidth = 1;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0.5;
        c.weighty = 0.5;

        JPanel p = new JPanel(grid);

        JScrollPane scrPane = new JScrollPane(p);
        scrPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrPane.setPreferredSize(frame.getPreferredSize());
        frame.add(scrPane, BorderLayout.CENTER);

        JSONObject contents;
        try {
            contents = new JSONObject(readFile(filename));
        } catch (org.json.JSONException e) {
            frame.getContentPane().removeAll();
            frame.add(new Label("Oops! There was an error parsing this JSON"));
            CustomButton cb = new CustomButton("Back", (eb) -> {
                renderDir(eb.getActionCommand(), progress);
            });
            cb.setActionCommand(Path.of(filename).getParent().toString());
            frame.revalidate();
            frame.repaint();
            return;
        }
        CustomButton cb = new CustomButton("Back", (e) -> {
            saveFile(filename, contents);
            renderDir(e.getActionCommand(), progress);
        });
        cb.setActionCommand(Path.of(filename).getParent().toString());
        p.add(cb, c);
        c.gridx = 1;
        p.add(new CustomButton("Mark Complete", (e) -> {
            setProgressOf(filename,progress,"complete");
        }), c);
        c.gridx = 0;
        c.gridy++;

        for (Iterator<String> it = contents.keys(); it.hasNext(); ) {
            String k = it.next();
            p.add(new Label(k), c);
            c.gridx = 1;
            JTextArea ta = new JTextArea(contents.getString(k));
            ta.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    contents.put(k,ta.getText());
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    contents.put(k,ta.getText());
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    contents.put(k,ta.getText());
                }
            });
            p.add(ta, c);
            c.gridx = 0;
            c.gridy++;
        }

        scrPane.revalidate();
        scrPane.repaint();

        frame.pack();
        frame.revalidate();
        frame.repaint();
    }
    static String getProgressOf(String absolutePath, JSONObject progress) {
        File subdir = new File(absolutePath);

        ArrayList<String> dirs = new ArrayList<>();

        while (!subdir.getAbsolutePath().equals(Paths.get(filepath,"Mods", project, "assets").toString()) && subdir.getParentFile() != null) {
            dirs.add(subdir.getName());
            subdir = subdir.getParentFile();
        }

        if (subdir.getParent() == null) {
            throw new RuntimeException("Error fetching progress of " + absolutePath);
        }

        for (int i = dirs.size() - 1; i > 0; i--) {
            progress = progress.getJSONObject(dirs.get(i));
        }

        return progress.getString(dirs.get(0));
    }
    static void setProgressOf(String absolutePath, JSONObject progress, String progressOf) {
        File subdir = new File(absolutePath);

        ArrayList<String> dirs = new ArrayList<>();

        while (!subdir.getAbsolutePath().equals(Paths.get(filepath,"Mods", project, "assets").toString()) && subdir.getParentFile() != null) {
            dirs.add(subdir.getName());
            subdir = subdir.getParentFile();
        }

        if (subdir.getParent() == null) {
            throw new RuntimeException("Error fetching progress of " + absolutePath);
        }

        for (int i = dirs.size() - 1; i > 0; i--) {
            progress = progress.getJSONObject(dirs.get(i));
        }

        progress.put(dirs.get(0), progressOf);
    }
    static void saveFile(String filename, JSONObject json) {
        try {
            FileWriter fw = new FileWriter(filename);
            fw.write(json.toString());
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    static String readFile(String filename) {
        StringBuilder contentLines = new StringBuilder();
        try {
            File file = new File(filename);
            List<String> lines = Files.readAllLines(file.toPath());
            for (String s : lines) {
                contentLines.append(s);
            }
        } catch (Exception e) {
            throw new RuntimeException();
        }
        return contentLines.toString();
    }
    static void copyFile(String source, String destination) {
        File src = new File(source);
        File target = new File(destination);
        try {
            Files.copy(src.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception err) {}
    }
    static boolean copyFileUnlessExists(String source, String destination) {
        File src = new File(source);
        File target = new File(destination);
        if (Files.exists(target.toPath())) {
            return false;
        }
        try {
            Files.copy(src.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception err) {
            System.out.println(err);
            return false;
        }
        return true;
    }
    static void newProject(ActionEvent e) {
        frame.getContentPane().removeAll();
        frame.add(new TextField("LanguageName"));
        frame.add(new TextField("YourNameNoSpaces"));
        frame.add(new CustomButton("Create Project", TranslationHelper::createProject));
        frame.revalidate();
        frame.repaint();
    }
    static void createProject(ActionEvent e) {
        project = ((TextField) frame.getContentPane().getComponent(0)).getText().replaceAll("\\s","");
        String name = ((TextField) frame.getContentPane().getComponent(1)).getText().replaceAll("\\s","");

        frame.getContentPane().removeAll();

        modId = name + "." + project;

        File modDir = new File(Paths.get(filepath, "Mods", project).toUri());
        boolean made = modDir.mkdir();
        if (!made) {
            frame.add(new Label("Failed to create project. Checking if it already exists..."));
            frame.revalidate();
            frame.repaint();
            if (!modDir.exists() || !modDir.isDirectory()) {
                frame.add(new Label(project + " could not be created or located"));
                frame.revalidate();
                frame.repaint();
                return;
            }
        }
        if (!Paths.get(filepath, "Mods", project, "manifest.json").toFile().exists()) {
            try {
                FileWriter manifest = new FileWriter(Paths.get(filepath, "Mods", project, "manifest.json").toFile());
                manifest.write("{\n" +
                        "\t\"Name\": \"" + project + "\",\n" +
                        "\t\"Author\": \"" + name + "\",\n" +
                        "\t\"Version\": \"0.0.1\",\n" +
                        "\t\"Description\": \"Translates the base game to " + project + "\",\n" +
                        "\t\"UniqueID\": \"" + modId + "\",\n" +
                        "\t\"UpdateKeys\": [],\n" +
                        "\t\"ContentPackFor\": {\n" +
                        "\t\t\"UniqueID\": \"Pathoschild.ContentPatcher\"\n" +
                        "\t}\n" +
                        "}");
                manifest.close();
            } catch (Exception err) {

            }
        }
        if (!Paths.get(filepath, "Mods", project, "content.json").toFile().exists()) {
            try {
                FileWriter content = new FileWriter(Paths.get(filepath, "Mods", project, "content.json").toFile());

                JSONObject contentJson = new JSONObject();
                contentJson.put("Format", "1.30.0");
                JSONArray changes = new JSONArray();
                contentJson.put("Changes", changes);

                JSONObject base = new JSONObject();
                base.put("Action", "EditData");
                base.put("Target", "Data/AdditionalLanguages");
                JSONObject entries = new JSONObject();
                JSONObject mainEntry = new JSONObject();
                mainEntry.put("ID", modId);
                mainEntry.put("LanguageCode", project.substring(0, 2).toLowerCase());//Should be changed later by user
                mainEntry.put("ButtonTexture", "Mods/" + modId + "/Button");
                mainEntry.put("UseLatinFont", true);
                mainEntry.put("TimeFormat", "[HOURS_24_00]:[MINUTES]");
                mainEntry.put("ClockTimeFormat", "[HOURS_24_00]:[MINUTES]");
                mainEntry.put("ClockDateFormat", "[DAY_OF_WEEK] [DAY_OF_MONTH]");
                entries.put(modId, mainEntry);
                base.put("Entries", entries);

                changes.remove(0);
                changes.put(base);

                JSONObject languageButton = new JSONObject();
                languageButton.put("Action", "Load");
                languageButton.put("Target", "Mods/" + modId + "/Button");
                languageButton.put("FromFile", "assets/button.png");

                changes.put(languageButton);

                content.write(contentJson.toString());

                content.close();
            } catch (Exception err) {

            }
        }

        File assetsDir = new File(Paths.get(filepath, "Mods", project, "assets").toUri());
        boolean assetsDirMade = assetsDir.mkdir();
        if (!assetsDirMade) {
            frame.add(new Label("Failed to create assets folder. Checking if it already exists..."));
            frame.revalidate();
            frame.repaint();
            if (!assetsDir.exists() || !assetsDir.isDirectory()) {
                frame.add(new Label("/assets/ could not be created or located"));
                frame.revalidate();
                frame.repaint();
                return;
            }
        }
        File src = new File("button.png");
        File target = new File(Paths.get(filepath, "Mods", project, "assets", "button.png").toUri());
        if (!target.exists()) {
            try {
                Files.copy(src.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception err) {
            }
        }
        try {
            File file = new File("TranslationHelper.properties");
            List<String> lines = Files.readAllLines(file.toPath());
            if (lines.get(1).split(",").length > 1) {
                lines.set(1, lines.get(1) + "," + project);
            } else {
                lines.set(1, lines.get(1) + " " + project);
            }
            Files.write(file.toPath(), lines);
        } catch (Exception err) {
            //Insufficient permissions
        }

        frame.revalidate();
        frame.repaint();

        beginProject();
    }
    static void initFirstMenu() {
        Label header = new Label("Choose your Stardew Valley install location");
        CustomButton b = new CustomButton("Choose",TranslationHelper::chooseDirectory);

        switch (OsCheck.getOperatingSystemType()) {
            case MacOS -> {
                fileChooser = new TextField(System.getProperty("user.home") + "/Library/Application Support/Steam/steamapps/Stardew Valley");
            }
            case Linux -> {
                fileChooser = new TextField(System.getProperty("user.home") + "/.local/share/Steam/steamapps/common/Stardew Valley");
            }
            default -> {
                fileChooser = new TextField("C:\\Program Files (x86)\\Steam\\steamapps\\common\\Stardew Valley");
            }
        }
        CustomButton next = new CustomButton("Next",TranslationHelper::next);

        frame.add(header);
        frame.add(fileChooser);
        frame.add(b);
        frame.add(next);
        frame.revalidate();
        frame.repaint();
    }
    static void chooseDirectory(ActionEvent e) {
        final JFileChooser fc = new JFileChooser(fileChooser.getText());
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int returnVal = fc.showDialog(frame, "Select");
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            fileChooser.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }
    static void next(ActionEvent e) {
        filepath = fileChooser.getText();
        frame.getContentPane().removeAll();
        frame.add(new Label("Verifying files..."));
        frame.revalidate();
        frame.repaint();

        if (Files.notExists(Paths.get(filepath))) {
            frame.add(new Label("The chosen directory does not exist!"));
            initFirstMenu();
            return;
        }
        Label currentStep = new Label("Directory exists...");
        frame.add(currentStep);
        frame.revalidate();
        frame.repaint();

        //Check for Stardew Valley
        boolean stardewValleyInstalled = switch (OsCheck.getOperatingSystemType()) {
            case Windows -> Files.notExists(Paths.get(filepath, "Stardew Valley.exe"));
            case MacOS, Other, Linux -> Files.notExists(Paths.get(filepath, "Stardew Valley"));
        };

        if (stardewValleyInstalled) {
            frame.getContentPane().removeAll();
            frame.add(new Label("Stardew Valley is not installed in the chosen directory. Please install Stardew Valley."));
            frame.add(new CustomButton("Close",(ae) -> System.exit(0)));
            frame.revalidate();
            frame.repaint();
            return;
        }
        currentStep.setText("Checking for SMAPI...");
        frame.revalidate();
        frame.repaint();
        System.out.println("Install SMAPI");

        boolean smapiInstalled = switch (OsCheck.getOperatingSystemType()) {
            case Windows -> Files.notExists(Paths.get(filepath, "StardewModdingAPI.exe"));
            case MacOS, Other, Linux -> Files.notExists(Paths.get(filepath, "StardewModdingAPI"));
        };

        if (smapiInstalled) {
            currentStep.setText("SMAPI is not installed in the chosen directory. Installing...");
            frame.revalidate();
            frame.repaint();
            if (!Installer.installSMAPI() || switch (OsCheck.getOperatingSystemType()) {
                case Windows -> Files.notExists(Paths.get(filepath, "StardewModdingAPI.exe"));
                case MacOS, Other, Linux -> Files.notExists(Paths.get(filepath, "StardewModdingAPI"));
            }) {
                frame.getContentPane().removeAll();
                frame.add(new Label("Failed to install SMAPI."));
                frame.add(new Label("You may have to run the install script manually:"));
                frame.add(new Label("1. In Steam -> Stardew Valley -> Settings -> Manage -> Browse Local Files"));
                frame.add(new Label("2. Find smapi_install -> SMAPI [Version] installer for developers"));
                frame.add(new Label("3. If you are on Linux, change the file permissions from Terminal:"));
                frame.add(new Label("sudo +xrw -R internal"));
                frame.add(new Label("4. Run the install script for your OS and follow the on screen instructions"));
                frame.add(new Label("5. Close and rerun StardewValleyTranslationHelper"));
                //chmod +xrw -R internal
                frame.add(new CustomButton("Close",(ae) -> System.exit(0)));
                frame.revalidate();
                frame.repaint();
                return;
            }
        }
        currentStep.setText("Checking for Content Patcher...");
        frame.revalidate();
        frame.repaint();
        System.out.println("Install Content Patcher");
        if (Files.notExists(Paths.get(TranslationHelper.filepath, "Mods","ContentPatcher"))) {
            currentStep.setText("Content Patcher is not installed in the chosen directory. Installing...");
            frame.revalidate();
            frame.repaint();
            if (!Installer.installContentPatcher()) {
                frame.getContentPane().removeAll();
                frame.add(new Label("Failed to install Content Patcher."));
                frame.add(new CustomButton("Close",(ae) -> System.exit(0)));
                frame.revalidate();
                frame.repaint();
                return;
            }
        }
        currentStep.setText("Checking for Stardew XNB Hack...");
        frame.revalidate();
        frame.repaint();
        System.out.println("Install XNB");
        if (Files.notExists(Paths.get(TranslationHelper.filepath, "Content (unpacked)"))) {
            currentStep.setText("Stardew XNB Hack is not installed in the chosen directory. Installing...");
            frame.revalidate();
            frame.repaint();
            if (!Installer.installXNBHack() || Files.notExists(Paths.get(TranslationHelper.filepath, "Content (unpacked)"))) {
                frame.getContentPane().removeAll();
                frame.add(new Label("Failed to install Stardew XNB Hack."));
                frame.add(new Label("Try manually running the script by following these instructions:"));
                frame.add(new Label("1. In Steam -> Stardew Valley -> Settings -> Manage -> Browse Local Files"));
                frame.add(new Label("2. Find and run StardewXnbHack" + (OsCheck.getOperatingSystemType() == OsCheck.OSType.Windows ? ".exe" : "")));
                frame.add(new Label("(If it doesn't run, try running in cmd/terminal using ./StardewXnbHack" + (OsCheck.getOperatingSystemType() == OsCheck.OSType.Windows ? ".exe" : "") + ")"));
                frame.add(new Label("3. Follow the on-screen instructions"));
                frame.add(new Label("4. Close and rerun StardewValleyTranslationHelper"));
                frame.add(new CustomButton("Close",(ae) -> System.exit(0)));
                frame.revalidate();
                frame.repaint();
                return;
            }
        }
        System.out.println("Finished XNB");
        frame.getContentPane().remove(currentStep);
        frame.add(new Label("Go to Steam -> Stardew Valley -> Properties -> Launch Options and set to "));
        String filetype = OsCheck.getOperatingSystemType() == OsCheck.OSType.Windows ? ".exe" : "";
        frame.add(new TextField(Paths.get(filepath,"StardewModdingAPI" + filetype) + " %command%"));
        frame.revalidate();
        frame.repaint();
        try {
            FileWriter config = new FileWriter("TranslationHelper.properties");
            config.append("location: ").append(TranslationHelper.filepath);
            config.append("\nprojects: \n");
            config.close();
            begin();
        } catch (Exception err) {
            //Insufficient permissions
        }
    }

}

class Installer {
    public static boolean installSMAPI() {
        final String version = "4.0.8";
        try {
            if (Files.notExists(Paths.get(TranslationHelper.filepath, "SMAPI.zip"))) {
                URL url = new URL("https://github.com/Pathoschild/SMAPI/releases/download/" + version + "/SMAPI-" + version + "-installer-for-developers.zip");
                ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
                FileOutputStream fileOutputStream = new FileOutputStream(Paths.get(TranslationHelper.filepath, "SMAPI.zip").toString());
                FileChannel fileChannel = fileOutputStream.getChannel();
                fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                fileOutputStream.close();

            }
            if (Files.notExists(Paths.get(TranslationHelper.filepath, "smapi_install"))) {
                unzip(Paths.get(TranslationHelper.filepath, "SMAPI.zip").toString(), Paths.get(TranslationHelper.filepath, "smapi_install").toString());
            }
            switch (OsCheck.getOperatingSystemType()) {
                case MacOS -> {
                    Runtime.
                            getRuntime().
                            exec("/bin/sh -c \"\" " + Q + Paths.get(TranslationHelper.filepath, "smapi_install", "SMAPI " + version + " installer for developers", "install on macOS.command") + Q);
                }
                case Linux -> {
                    Runtime.
                            getRuntime().
                            exec("/bin/sh -c \"\" " + Q + Paths.get(TranslationHelper.filepath, "smapi_install", "SMAPI " + version + " installer for developers", "install on Linux.sh") + Q);
                }
                case Windows -> {
                    Runtime.
                            getRuntime().
                            exec("cmd /c start \"\" " + Q + Paths.get(TranslationHelper.filepath, "smapi_install", "SMAPI " + version + " installer for developers", "install on Windows.bat") + Q);
                }
                default -> {
                    return false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static boolean installContentPatcher() {
        try {
            if (Files.notExists(Paths.get(TranslationHelper.filepath, "contentPatcher.zip"))) {
                URL url = new URL("https://www.curseforge.com/api/v1/mods/309243/files/5197203/download");
                ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
                FileOutputStream fileOutputStream = new FileOutputStream(Paths.get(TranslationHelper.filepath, "contentPatcher.zip").toString());
                FileChannel fileChannel = fileOutputStream.getChannel();
                fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                fileOutputStream.close();
            }
            if (Files.notExists(Paths.get(Paths.get(TranslationHelper.filepath, "Mods", "ContentPatcher").toString()))) {
                unzip(Paths.get(TranslationHelper.filepath, "contentPatcher.zip").toString(), Paths.get(TranslationHelper.filepath, "Mods").toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    public static boolean installXNBHack() {
        final String version = "1.1.0";
        try {
            if (Files.notExists(Paths.get(TranslationHelper.filepath, "XNBHack.zip"))) {
                URL url = switch (OsCheck.getOperatingSystemType()) {
                    case MacOS -> new URL("https://github.com/Pathoschild/StardewXnbHack/releases/download/" + version + "/StardewXnbHack-" + version + "-for-macOS.zip");
                    case Linux -> new URL("https://github.com/Pathoschild/StardewXnbHack/releases/download/" + version + "/StardewXnbHack-" + version + "-for-Linux.zip");
                    case Windows -> new URL("https://github.com/Pathoschild/StardewXnbHack/releases/download/" + version + "/StardewXnbHack-" + version + "-for-Windows.zip");
                    default -> null;
                };
                if (url == null) {
                    return false;
                }
                ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
                FileOutputStream fileOutputStream = new FileOutputStream(Paths.get(TranslationHelper.filepath, "XNBHack.zip").toString());
                FileChannel fileChannel = fileOutputStream.getChannel();
                fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                unzip(Paths.get(TranslationHelper.filepath, "XNBHack.zip").toString(), Paths.get(TranslationHelper.filepath).toString());
                fileOutputStream.close();
            }
            switch (OsCheck.getOperatingSystemType()) {
                case MacOS -> {
                    if (Files.notExists(Paths.get(TranslationHelper.filepath, "StardewXnbHack.command"))) {
                        File exe = new File(Paths.get(TranslationHelper.filepath, "StardewXnbHack " + version + " for macOS","StardewXnbHack.command").toString());
                        exe.renameTo(new File(Paths.get(TranslationHelper.filepath, "StardewXnbHack.command").toString()));
                    }
                    ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", "\"\"", String.valueOf(Paths.get(TranslationHelper.filepath, "StardewXnbHack.command")));
                    Process runtimeProcess = pb.start();
                    runtimeProcess.waitFor();
                }
                case Linux -> {
                    if (Files.notExists(Paths.get(TranslationHelper.filepath, "StardewXnbHack"))) {
                        File exe = new File(Paths.get(TranslationHelper.filepath, "StardewXnbHack " + version + " for Linux","StardewXnbHack").toString());
                        exe.renameTo(new File(Paths.get(TranslationHelper.filepath, "StardewXnbHack").toString()));
                    }
                    ProcessBuilder pb_chmod = new ProcessBuilder("/bin/sh", "-c", "chmod +x " + Q + Paths.get(TranslationHelper.filepath, "StardewXnbHack") + Q);
                    Process runtimeProcess_pb_chmod = pb_chmod.start();
                    runtimeProcess_pb_chmod.waitFor();

                    ProcessBuilder pb = new ProcessBuilder(String.valueOf(Paths.get(TranslationHelper.filepath, "StardewXnbHack")));
                    pb.directory(new File(TranslationHelper.filepath));
                    Process runtimeProcess = pb.start();

                    runtimeProcess.waitFor();
                }
                case Windows -> {
                    if (Files.notExists(Paths.get(TranslationHelper.filepath, "StardewXnbHack.exe"))) {
                        File exe = new File(Paths.get(TranslationHelper.filepath, "StardewXnbHack " + version + " for Windows","StardewXnbHack.exe").toString());
                        exe.renameTo(new File(Paths.get(TranslationHelper.filepath, "StardewXnbHack.exe").toString()));
                    }
                    System.out.println("cmd /c " + Paths.get(TranslationHelper.filepath, "StardewXnbHack.exe"));
                    ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", ".\\", String.valueOf(Paths.get(TranslationHelper.filepath, "StardewXnbHack.exe")));
                    Process runtimeProcess = pb.start();
                    runtimeProcess.waitFor();
                }
                default -> {
                    return false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    public static void unzip(String fileZip, String targetDir) throws Exception {
        File destDir = new File(targetDir);

        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(fileZip));
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
            File newFile = newFile(destDir, zipEntry);
            if (zipEntry.isDirectory()) {
                if (!newFile.isDirectory() && !newFile.mkdirs()) {
                    throw new IOException("Failed to create directory " + newFile);
                }
            } else {
                // fix for Windows-created archives
                File parent = newFile.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Failed to create directory " + parent);
                }

                // write file content
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }
            zipEntry = zis.getNextEntry();
        }

        zis.closeEntry();
        zis.close();
    }
    public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }
}

class MainFrame extends JFrame implements WindowListener {

    public MainFrame(String title) {
        super(title);
        setLayout(new FlowLayout());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().add(new Label(), BorderLayout.CENTER);
        setPreferredSize(new Dimension(800, 600));
        pack();
        addWindowListener(this);

        setVisible(true);
    }

    public void windowClosing(WindowEvent e) {
        dispose();
    }

    public void windowOpened(WindowEvent e) {this.revalidate();this.repaint();}
    public void windowActivated(WindowEvent e) {this.revalidate();this.repaint();}
    public void windowIconified(WindowEvent e) {this.revalidate();this.repaint();}
    public void windowDeiconified(WindowEvent e) {this.revalidate();this.repaint();}
    public void windowDeactivated(WindowEvent e) {this.revalidate();this.repaint();}
    public void windowClosed(WindowEvent e) {}

}

class CustomButton extends Button implements ActionListener {
    Consumer<ActionEvent> f;
    CustomButton(String label, Consumer<ActionEvent> function) {
        super(label);
        f = function;
        this.addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        f.accept(e);
    }

}

/**
 * helper class to check the operating system this Java VM runs in
 * please keep the notes below as a pseudo-license
 * <a href="http://stackoverflow.com/questions/228477/how-do-i-programmatically-determine-operating-system-in-java">...</a>
 * compare to <a href="http://svn.terracotta.org/svn/tc/dso/tags/2.6.4/code/base/common/src/com/tc/util/runtime/Os.java">...</a>
 * <a href="http://www.docjar.com/html/api/org/apache/commons/lang/SystemUtils.java.html">...</a>
 */
class OsCheck {
    /**
     * types of Operating Systems
     */
    public enum OSType {
        Windows, MacOS, Linux, Other
    };

    // cached result of OS detection
    protected static OSType detectedOS;

    /**
     * detect the operating system from the os.name System property and cache
     * the result
     * {@code @returns} - the operating system detected
     */
    public static OSType getOperatingSystemType() {
        if (detectedOS == null) {
            String OS = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
            if ((OS.contains("mac")) || (OS.contains("darwin"))) {
                detectedOS = OSType.MacOS;
            } else if (OS.contains("win")) {
                detectedOS = OSType.Windows;
            } else if (OS.contains("nux")) {
                detectedOS = OSType.Linux;
            } else {
                detectedOS = OSType.Other;
            }
        }
        return detectedOS;
    }
}