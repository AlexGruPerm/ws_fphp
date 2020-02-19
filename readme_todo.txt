1) when we run Task.foreachPar(seqResDicts.dicts) and size of dicts is 10.
on 4 cores CPU, it executing by no more than 8 threads. First step 8 and second step 2 threads.
bcs.
https://github.com/zio/zio/issues/297
https://github.com/zio/zio/pull/295#issuecomment-428927804
http://degoes.net/articles/zio-threads

UPD: I've misread your comment a bit. So, regarding the primary pool.
The configuration (cores+1) already assumes that the pool takes the maximum computational throughput.
So in case we got a rejection - most likely the queue would continue growing and at the end the app will die or
computations will terminate. So, there is no real need (IMO) to set non-zero queue for computational pool.
In case it's being overscheduled - in almost all the cases nothing may save us (this is not true only for apps with
specific load profile, which are idle almost all the time but have peaks of high CPU intensity)

Primary ZIO thread pool upper boundary should be intended for computations only and limited to core_number + 1 threads.
Queue size should be set to zero.

!!! There is no our case because we don't need context switching between our "independent" like green threads!

CPUs only have a certain number of cores, each of which can execute tasks more or less independently of the other cores.
If we create more threads than cores, then the operating system spends a lot of time switching between threads
(context switching), because the hardware can’t physically run them all at the same time. So ideally,
to be maximally efficient, we would only create as many threads as there are cores, to minimize context switching.
