import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class TranslationHelper {
    private static MainFrame frame;
    public static String filepath;
    private static TextField fileChooser;
    public static void main(String[] args) {
        frame = new MainFrame("Stardew Valley Translation Tool");

        Label header = new Label("Choose your Stardew Valley install location");
        CustomButton b = new CustomButton("Choose",TranslationHelper::chooseDirectory);

        fileChooser = new TextField("C:\\Program Files (x86)\\Steam\\steamapps\\common\\Stardew Valley");

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
            Label header = new Label("Choose your Stardew Valley install location");
            CustomButton b = new CustomButton("Choose",TranslationHelper::chooseDirectory);

            fileChooser = new TextField("C:\\Program Files (x86)\\Steam\\steamapps\\common\\Stardew Valley");

            CustomButton next = new CustomButton("Next",TranslationHelper::next);

            frame.add(header);
            frame.add(fileChooser);
            frame.add(b);
            frame.add(next);
            frame.revalidate();
            frame.repaint();
            return;
        }
        Label currentStep = new Label("Directory exists...");
        frame.add(currentStep);
        frame.revalidate();
        frame.repaint();

        //Check for Stardew Valley
        if (Files.notExists(Paths.get(filepath + "\\Stardew Valley.exe"))) {
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
        if (Files.notExists(Paths.get(filepath + "\\smapi.exe"))) {
            currentStep.setText("SMAPI is not installed in the chosen directory. Installing...");
            frame.revalidate();
            frame.repaint();
            Installer.installSMAPI();
        }
        currentStep.setText("Checking for Content Patcher...");
        frame.revalidate();
        frame.repaint();
    }
}

class Installer {
    public static void installSMAPI() {
        try {
            URL url = new URL("https://github.com/Pathoschild/SMAPI/releases/download/4.0.2/SMAPI-4.0.2-installer-for-developers.zip");
            ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
            FileOutputStream fileOutputStream = new FileOutputStream(TranslationHelper.filepath + "\\SMAPI.zip");
            FileChannel fileChannel = fileOutputStream.getChannel();
            fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);

            unzip(TranslationHelper.filepath + "\\SMAPI.zip",TranslationHelper.filepath + "\\smapi_install");
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        setPreferredSize(new Dimension(800, 300));
        pack();
        addWindowListener(this);

        setVisible(true);
    }

    public void windowClosing(WindowEvent e) {
        dispose();
    }

    public void windowOpened(WindowEvent e) {}
    public void windowActivated(WindowEvent e) {}
    public void windowIconified(WindowEvent e) {}
    public void windowDeiconified(WindowEvent e) {}
    public void windowDeactivated(WindowEvent e) {}
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