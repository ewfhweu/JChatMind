package com.kama.jchatmind.service;

import java.util.List;

public interface RagService {
    float[] embed(String text);
    List<float[]> batchEmbed(List<String> texts);

    List<String> similaritySearch(String kbId, String title);
}
