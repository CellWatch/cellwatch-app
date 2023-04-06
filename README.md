# Android ndt8 implementation

## Testing with a local server

By default, the app is set up to run against one of M-Lab's servers. If you want to run the server locally, clone and run it:

```
git clone https://github.com/m-lab/msak.git
git checkout sandbox-roberto-server
go build ./cmd/msak-server
./msak-server
```

Then, in `FirstFragment.kt`, uncomment the lines following `// use local server for testing` and comment out the line following `// use real M-Lab server`.

## TODO

- add licensing info for msak (https://github.com/robertodauria/msak) and AndroidPing (https://github.com/dburckh/AndroidPing).
