package test;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//


import cn.edu.pku.echo.icc_sniffer.main.APKContainer;

public class Main {
    public Main() {
    }

    public static void main(String[] args) {
        System.out.println("###### Icc-Sniffer starts ######");
        String pkg = "/Users/apple/Downloads/com.sohu.newsclient.apk";
        if (args.length < 1) {
            System.out.println("No args to specify apk path!");
        } else {
            pkg = args[0];
        }

        APKContainer apk_container = new APKContainer(pkg);
        apk_container.execute(3);
    }
}

