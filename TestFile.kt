
import java.io.File

fun main() {
    val parent = File("/data/user/0/app/files/projects/test")
    val f = File(parent, "/root/test.kt").normalize().absolutePath
    println(f)
}
    