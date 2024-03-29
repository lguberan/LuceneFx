/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.guberan.lucenefx;

import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.icu.ICUFoldingFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@link LowerCaseFilter} and {@link StopFilter}, using a list of English stop
 * words.
 */
public final class NoAccentAnalyzer extends StopwordAnalyzerBase {

    /**
     * Default maximum allowed token length
     */
    public static final int DEFAULT_MAX_TOKEN_LENGTH = 255;

    private int maxTokenLength = DEFAULT_MAX_TOKEN_LENGTH;

    // my stop words (English)
    private static final List<String> stopWordsEN = Arrays.asList("a", "an", "and", "are", "as", "at", "be", "but",
            "by", "for", "if", "in", "into", "is", "it", "no", "not", "of", "on", "or", "such", "that", "the", "their",
            "then", "there", "these", "they", "this", "to", "was", "will", "with");

    // my stop words (French)
    private static final List<String> stopWordsFR = Arrays.asList("a", "ai", "aie", "aient", "aies", "ait", "as", "au",
            "aura", "aurai", "auraient", "aurais", "aurait", "auras", "aurez", "auriez", "aurions", "aurons", "auront",
            "aux", "avaient", "avais", "avait", "avec", "avez", "aviez", "avions", "avons", "ayant", "ayez", "ayons",
            "c", "ce", "ceci", "cela", "cela", "ces", "cet", "cette", "d", "dans", "de", "des", "du", "elle", "en",
            "es", "est", "et", "etaient", "etais", "etait", "etant", "ete", "etee", "etees", "etes", "etes", "etiez",
            "etions", "eu", "eue", "eues", "eumes", "eurent", "eus", "eusse", "eussent", "eusses", "eussiez",
            "eussions", "eut", "eut", "eutes", "eux", "fumes", "furent", "fus", "fusse", "fussent", "fusses", "fussiez",
            "fussions", "fut", "fut", "futes", "ici", "il", "ils", "j", "je", "l", "la", "le", "les", "leur", "leurs",
            "lui", "m", "ma", "mais", "me", "meme", "mes", "moi", "mon", "n", "ne", "nos", "notre", "nous", "on", "ont",
            "ou", "par", "pas", "pour", "qu", "que", "quel", "quelle", "quelles", "quels", "qui", "s", "sa", "sans",
            "se", "sera", "serai", "seraient", "serais", "serait", "seras", "serez", "seriez", "serions", "serons",
            "seront", "ses", "soi", "soient", "sois", "soit", "sommes", "son", "sont", "soyez", "soyons", "suis", "sur",
            "t", "ta", "te", "tes", "toi", "ton", "tu", "un", "une", "vos", "votre", "vous", "y");

    /**
     * An unmodifiable set containing some common words that are usually not useful for searching.
     */
    public static final CharArraySet STOP_WORDS_SET;

    static {
        List<String> stopWords = new ArrayList<>(stopWordsEN);
        stopWords.addAll(stopWordsFR);
        stopWords.sort(null);
        final CharArraySet stopSet = new CharArraySet(stopWords, false);
        STOP_WORDS_SET = CharArraySet.unmodifiableSet(stopSet);
    }

    /**
     * Builds an analyzer with the given stop words.
     *
     * @param stopWords stop words
     */
    public NoAccentAnalyzer(CharArraySet stopWords) {
        super(stopWords);
    }

    /**
     * Builds an analyzer with the default stop words ({@link #STOP_WORDS_SET}).
     */
    public NoAccentAnalyzer() {
        this(STOP_WORDS_SET);
    }

    /**
     * Builds an analyzer with the stop words from the given reader.
     *
     * @param stopwords Reader to read stop words from
     * @see WordlistLoader#getWordSet(Reader)
     */
    public NoAccentAnalyzer(Reader stopwords) throws IOException {
        this(loadStopwordSet(stopwords));
    }

    /**
     * Set maximum allowed token length. If a token is seen that exceeds this length
     * then it is discarded. This setting only takes effect the next time
     * tokenStream or tokenStream is called.
     */
    public void setMaxTokenLength(int length) {
        maxTokenLength = length;
    }

    /**
     * @see #setMaxTokenLength
     */
    public int getMaxTokenLength() {
        return maxTokenLength;
    }

    @Override
    protected TokenStream normalize(String fieldName, TokenStream in) {
        return new LowerCaseFilter(in);
    }

    @Override
    protected TokenStreamComponents createComponents(final String fieldName) {
        final StandardTokenizer src = new StandardTokenizer();
        src.setMaxTokenLength(maxTokenLength);
        TokenStream tok = new ICUFoldingFilter(src); // replace LowerCaseFilter + ASCIIFoldingFilter
        tok = new StopFilter(tok, stopwords);
        return new TokenStreamComponents(reader -> {
            src.setMaxTokenLength(NoAccentAnalyzer.this.maxTokenLength);
            src.setReader(reader);
        }, tok);
    }
}
