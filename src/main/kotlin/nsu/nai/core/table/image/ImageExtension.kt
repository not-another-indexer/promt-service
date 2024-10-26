package nsu.nai.core.table.image

enum class ImageExtension(val extension: String) {
    PNG("png"),
    JPG("jpg"),
    JPEG("jpeg");

    companion object {
        fun parse(extension: String): ImageExtension? {
            return  try{
                ImageExtension.valueOf(extension.uppercase())
            } catch (e : IllegalArgumentException){
                null
            }
        }
    }
}