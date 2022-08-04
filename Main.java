package analyzer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {

        String files = args[0];
        String patternDBName = args[1];
        
        File patternDB = new File(patternDBName);
        String dbLine;
        String[] splitLine;
        StringBuilder pat;
        StringBuilder nam;
        ArrayList<FileTypePattern> patterns = new ArrayList<>();
        try {
            Scanner DBScanner = new Scanner(patternDB);
            while (DBScanner.hasNextLine()) {
                dbLine = DBScanner.nextLine();
                splitLine = dbLine.split(";\"");
                pat = new StringBuilder(splitLine[1]);
                pat.deleteCharAt(pat.length() - 1);
                nam = new StringBuilder(splitLine[2]);
                nam.deleteCharAt(nam.length() - 1);
                patterns.add(new FileTypePattern(splitLine[0], pat, nam));
            }
            DBScanner.close();
        } catch (FileNotFoundException e) {
            System.out.println("file not found");
            e.printStackTrace();
        }

        File filesDir = new File(files);
        String[] fileNames = filesDir.list();
        int fileNum = fileNames.length;

        String[] fileType = matchFileTypesStr(files, fileNames, patterns);

        for (int i = 0; i < fileNum; i++) {
            System.out.println(fileNames[i] + ": " + fileType[i]);
        }
    }

    public static String[] matchFileTypesStr(String files, String[] fileNames, ArrayList<FileTypePattern> patterns) {
        int fileNum = fileNames.length;
        String[] fileType = new String[fileNum];
        boolean[] foundType = new boolean[fileNum];
        Arrays.fill(foundType, false);
        boolean allFound = false;
        boolean prevFound;

        String P;
        String typeName;
        FileTypeThread[] typeThreads = new FileTypeThread[fileNum];

        int Pindex = patterns.size() - 1;
        while (Pindex >= 0 && !allFound) {
            P = patterns.get(Pindex).getPattern();
            typeName = patterns.get(Pindex).getTypeName();
            byte[] PBytes = P.getBytes();
            for (int i = 0; i < fileNum; i++) {
                if (!foundType[i]) {
                    typeThreads[i] = new FileTypeThread(files, fileNames[i], PBytes);
                    typeThreads[i].start();
                }
            }

            prevFound = true;
            for (int i = 0; i < fileNum; i++) {
                if (!foundType[i]) {
                    try {
                        typeThreads[i].join();
                        foundType[i] = typeThreads[i].getType();
                        fileType[i] = foundType[i] ? typeName : "Unknown file type";
                        prevFound = prevFound && foundType[i];
                    } catch (InterruptedException e) {
                        System.out.println("thread " + i + " is interrupted");
                        e.printStackTrace();
                    }
                }
            }
            allFound = prevFound;
            Pindex--;
        }
        return fileType;
    }

    public static boolean findPNaive(byte[] P, byte[] file) {
        int Pleng = P.length;
        int fileSize = file.length;
        boolean similar;
        boolean isSame = false;
        int i = 0;
        int j;
        while (i <= fileSize - Pleng && !isSame) {
            similar = file[i] == P[0];
            j = 1;
            while (j < Pleng && similar) {
                similar = file[i + j] == P[j];
                j++;
            }
            isSame = similar;
            i++;
        }
        return isSame;
    }

    public static int[] prefixBytesFunc(byte[] str) {
        int l = str.length;
        int[] p = new int[l];
        p[0] = 0;
        int j;
        boolean equ;
        for (int i = 1; i < l; i++) {
            j = p[i - 1];
            equ = str[i] == str[j];
            while (j > 0 && !equ) {
                j = p[j - 1];
                equ = str[i] == str[j];
            }
            if (j == 0 && !equ) {
                p[i] = 0;
            } else {
                p[i] = j + 1;
            }
        }
        return p;
    }

    public static boolean findP_KMP(byte[] sub, byte[] str) {

        int[] p = prefixBytesFunc(sub);
        int strL = str.length;
        int subL = sub.length;
        boolean same = false;
        int i = 0;
        int j = 0;
        int k;
        boolean equ;
        while (i <= strL - subL && !same) {
            equ = str[i + j] == sub[j];
            while (j < subL - 1 && equ) {
                j++;
                equ = str[i + j] == sub[j];
            }
            same = equ;
            if (!same) {
                if (j == 0) {
                    i++;
                } else {
                    i = i + j - p[j - 1];
                    j = p[j - 1];
                }
            }
        }
        return same;
    }

    public static boolean findPHash(byte[] sub, byte[] str) {
        int subL = sub.length;
        int s = 2;
        int m = 257;
        int subH = strPolyHash(sub, s, m);
        boolean same = false;
        int i = str.length - subL;
        byte[] lastBytes = new byte[subL];
        if (i >= 0) {
            System.arraycopy(str, i, lastBytes, 0, subL);
            int strH = strPolyHash(lastBytes, s, m);
            if (strH == subH) {
                same = Arrays.equals(lastBytes, sub);
            }
            while (i > 0 && !same) {
                i--;
                strH = strPolyHashMove(str[i + subL], str[i], strH, subL, s, m);
                if (strH == subH) {
                    System.arraycopy(str, i, lastBytes, 0, subL);
                    same = Arrays.equals(lastBytes, sub);
                }
            }
        }

        return same;
    }

    public static int strPolyHash(byte[] str, int s, int m) {
        int h = 0;
        for (int i = 0; i < str.length; i++) {
            h = h + str[i] * ((int) Math.pow(s, i));
        }
        return h;
    }

    public static int strPolyHashMove(byte strRemove, byte strAdd, int oldHash, int strL, int s, int m) {
        int h = (oldHash - strRemove * ((int) Math.pow(s, strL - 1))) * s + strAdd;
        return h;
    }
}

class FileTypeThread extends Thread {
    String dir;
    String file;
    byte[] PBytes;
    boolean isType;
    public FileTypeThread(String dir, String file, byte[] PBytes) {
        this.dir = dir;
        this.file = file;
        this.PBytes = PBytes;
    }

    @Override
    public void run() {
        try {
            byte[] fileBytes = Files.readAllBytes(Paths.get(this.dir, this.file));
            this.isType = Main.findPHash(this.PBytes, fileBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean getType() {
        return this.isType;
    }

    public String getFile() {
        return this.file;
    }
}

class FileTypePattern {
    String pattern;
    String typeName;
    int priority;
    public FileTypePattern(String priority, StringBuilder pattern, StringBuilder typeName) {
        this.pattern = String.valueOf(pattern);
        this.typeName = String.valueOf(typeName);
        this.priority = Integer.parseInt(priority);
    }

    public String getPattern() {
        return this.pattern;
    }

    public String getTypeName() {
        return this.typeName;
    }
}
