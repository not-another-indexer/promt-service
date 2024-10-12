package nsu.nai.core

/**
 * Параметры, используемые для поиска контента в хранилище.
 */
enum class Parameter {
    /**
     * Семантическое сходство с одним элементом.
     */
    SEMANTIC_ONE_PEACE_SIMILARITY,

    /**
     * Сходство с распознанным текстом.
     */
    RECOGNIZED_TEXT_SIMILARITY,

    /**
     * Сходство с текстовым описанием.
     */
    TEXTUAL_DESCRIPTION_SIMILARITY,

    /**
     * Сходство с распознанным лицом.
     */
    RECOGNIZED_FACE_SIMILARITY,

    /**
     * Ранжирование по распознанному тексту с использованием BM25.
     */
    RECOGNIZED_TEXT_BM25_RANK,

    /**
     * Ранжирование по текстовому описанию с использованием BM25.
     */
    TEXTUAL_DESCRIPTION_BM25_RANK,

    /**
     * Нераспознанный параметр.
     */
    UNRECOGNIZED
}