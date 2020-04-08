
val foo :Double = 69.60
val pow :Int = 100

val res = pow * foo
println(res);
//res: Double = 6959.999999999999

println(res.floor)
//6959.0

println(6960.floor)
//6960.0

println(6960.toFloat.floor)
//6960.0