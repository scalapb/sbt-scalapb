import com.trueaccord.scalapb.{ScalaPbPlugin => PB}

PB.protobufSettings

version in PB.protobufConfig := "3.0.0"

PB.runProtoc in PB.protobufConfig := { args =>
  com.github.os72.protocjar.Protoc.runProtoc("-v300" +: args.toArray)
}

excludeFilter in PB.protobufConfig := "test1.proto"
