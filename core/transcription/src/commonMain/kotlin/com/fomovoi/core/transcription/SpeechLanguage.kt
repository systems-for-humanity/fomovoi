package com.fomovoi.core.transcription

/**
 * Supported languages for speech recognition with their model configurations.
 */
enum class SpeechLanguage(
    val code: String,
    val displayName: String,
    val modelId: String,
    val baseUrl: String,
    val encoderFile: ModelFile,
    val decoderFile: ModelFile,
    val joinerFile: ModelFile,
    val tokensFile: ModelFile
) {
    ENGLISH(
        code = "en",
        displayName = "English",
        modelId = "sherpa-onnx-streaming-zipformer-en-2023-02-21",
        baseUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-02-21/resolve/main",
        encoderFile = ModelFile("encoder-epoch-99-avg-1.onnx", 292_543_537L),
        decoderFile = ModelFile("decoder-epoch-99-avg-1.onnx", 2_093_080L),
        joinerFile = ModelFile("joiner-epoch-99-avg-1.onnx", 1_026_462L),
        tokensFile = ModelFile("tokens.txt", 5_048L)
    ),
    CHINESE(
        code = "zh",
        displayName = "Chinese (Mandarin)",
        modelId = "sherpa-onnx-streaming-zipformer-zh-2023-02-21",
        baseUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-zh-2023-02-21/resolve/main",
        encoderFile = ModelFile("encoder-epoch-99-avg-1.onnx", 292_543_537L),
        decoderFile = ModelFile("decoder-epoch-99-avg-1.onnx", 12_823_448L),
        joinerFile = ModelFile("joiner-epoch-99-avg-1.onnx", 6_291_866L),
        tokensFile = ModelFile("tokens.txt", 98_188L)
    ),
    JAPANESE(
        code = "ja",
        displayName = "Japanese",
        modelId = "sherpa-onnx-streaming-zipformer-ja-2023-09-29",
        baseUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-ja-2023-09-29/resolve/main",
        encoderFile = ModelFile("encoder-epoch-99-avg-1.onnx", 292_543_537L),
        decoderFile = ModelFile("decoder-epoch-99-avg-1.onnx", 12_823_448L),
        joinerFile = ModelFile("joiner-epoch-99-avg-1.onnx", 6_291_866L),
        tokensFile = ModelFile("tokens.txt", 54_068L)
    ),
    KOREAN(
        code = "ko",
        displayName = "Korean",
        modelId = "sherpa-onnx-streaming-zipformer-korean-2024-06-16",
        baseUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-korean-2024-06-16/resolve/main",
        encoderFile = ModelFile("encoder-epoch-99-avg-1.onnx", 292_543_537L),
        decoderFile = ModelFile("decoder-epoch-99-avg-1.onnx", 5_119_640L),
        joinerFile = ModelFile("joiner-epoch-99-avg-1.onnx", 2_512_282L),
        tokensFile = ModelFile("tokens.txt", 12_044L)
    ),
    GERMAN(
        code = "de",
        displayName = "German",
        modelId = "sherpa-onnx-streaming-zipformer-de-2023-06-26",
        baseUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-de-2023-06-26/resolve/main",
        encoderFile = ModelFile("encoder-epoch-99-avg-1.onnx", 292_543_537L),
        decoderFile = ModelFile("decoder-epoch-99-avg-1.onnx", 2_093_080L),
        joinerFile = ModelFile("joiner-epoch-99-avg-1.onnx", 1_026_462L),
        tokensFile = ModelFile("tokens.txt", 5_016L)
    ),
    FRENCH(
        code = "fr",
        displayName = "French",
        modelId = "sherpa-onnx-streaming-zipformer-fr-2023-04-14",
        baseUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-fr-2023-04-14/resolve/main",
        encoderFile = ModelFile("encoder-epoch-99-avg-1.onnx", 292_543_537L),
        decoderFile = ModelFile("decoder-epoch-99-avg-1.onnx", 2_093_080L),
        joinerFile = ModelFile("joiner-epoch-99-avg-1.onnx", 1_026_462L),
        tokensFile = ModelFile("tokens.txt", 5_016L)
    ),
    SPANISH(
        code = "es",
        displayName = "Spanish",
        modelId = "sherpa-onnx-streaming-zipformer-es-2023-06-21",
        baseUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-es-2023-06-21/resolve/main",
        encoderFile = ModelFile("encoder-epoch-99-avg-1.onnx", 292_543_537L),
        decoderFile = ModelFile("decoder-epoch-99-avg-1.onnx", 2_093_080L),
        joinerFile = ModelFile("joiner-epoch-99-avg-1.onnx", 1_026_462L),
        tokensFile = ModelFile("tokens.txt", 5_016L)
    ),
    RUSSIAN(
        code = "ru",
        displayName = "Russian",
        modelId = "sherpa-onnx-streaming-zipformer-ru-2023-06-26",
        baseUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-ru-2023-06-26/resolve/main",
        encoderFile = ModelFile("encoder-epoch-99-avg-1.onnx", 292_543_537L),
        decoderFile = ModelFile("decoder-epoch-99-avg-1.onnx", 2_093_080L),
        joinerFile = ModelFile("joiner-epoch-99-avg-1.onnx", 1_026_462L),
        tokensFile = ModelFile("tokens.txt", 5_016L)
    );

    val modelFiles: List<ModelFile>
        get() = listOf(encoderFile, decoderFile, joinerFile, tokensFile)

    val totalSize: Long
        get() = modelFiles.sumOf { it.expectedSize }

    companion object {
        fun fromCode(code: String): SpeechLanguage? = entries.find { it.code == code }

        val default: SpeechLanguage = ENGLISH
    }
}

data class ModelFile(
    val name: String,
    val expectedSize: Long
)
