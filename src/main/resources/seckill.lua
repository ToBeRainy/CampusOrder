-- 参数列表
local voucherId = ARGV[1]
local userId = ARGV[2]

-- 数据 key
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 库存校验
if (tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end

-- 一人一单校验
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

-- 扣减库存并记录用户下单
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
return 0
