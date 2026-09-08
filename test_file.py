import os
import subprocess

with open('TestFile.kt', 'w') as f:
    f.write("""
import java.io.File

fun main() {
    val parent = File("/data/user/0/app/files/projects/test")
    val f = File(parent, "/root/test.kt").normalize().absolutePath
    println(f)
}
    """)
subprocess.run(["kotlinc", "-script", "TestFile.kt"])
