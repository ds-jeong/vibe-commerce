package com.example.ecommerce.global;

import java.util.regex.Pattern;

public final class KoreanNames {

    public static final String MESSAGE = "이름은 한글 2~20자로 입력해 주세요.";
    private static final Pattern PATTERN = Pattern.compile("^[가-힣]+(?: [가-힣]+)*$");

    private KoreanNames() {
    }

    public static boolean isValid(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        return trimmed.length() >= 2 && trimmed.length() <= 20 && PATTERN.matcher(trimmed).matches();
    }
}
