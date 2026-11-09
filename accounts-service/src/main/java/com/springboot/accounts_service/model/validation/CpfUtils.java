package com.springboot.accounts_service.model.validation;

public class CpfUtils {

    public static boolean isValid(String document) {
        if (document.length() != 11 || document.matches("^(\\d)\\1{10}$")) {
            return false;
        }

        char dig10, dig11;
        int sm, i, r, num, peso;

        try {
            // Cálculo do 10º dígito verificador
            sm = 0;
            peso = 10;
            for (i = 0; i < 9; i++) {
                num = (int)(document.charAt(i) - 48);
                sm = sm + (num * peso);
                peso = peso - 1;
            }

            r = 11 - (sm % 11);
            if ((r == 10) || (r == 11)) {
                dig10 = '0';
            } else {
                dig10 = (char)(r + 48);
            }

            // Cálculo do 11º dígito verificador
            sm = 0;
            peso = 11;
            for (i = 0; i < 10; i++) {
                num = (int)(document.charAt(i) - 48);
                sm = sm + (num * peso);
                peso = peso - 1;
            }

            r = 11 - (sm % 11);
            if ((r == 10) || (r == 11)) {
                dig11 = '0';
            } else {
                dig11 = (char)(r + 48);
            }

            // Verifica se os dígitos calculados conferem com os dígitos informados
            return (dig10 == document.charAt(9)) && (dig11 == document.charAt(10));
        } catch (Exception e) {
            return false;
        }
    }

    public static String normalizeDocument(String document) {
        return document.replaceAll("\\D", "");
    }
}
