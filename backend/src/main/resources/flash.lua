local dealId = ARGV[1]
local userId = ARGV[2]

-- 拼接redis key
local stockKey = 'flash:stock:' .. dealId
local orderKey = 'flash:order:' .. dealId

-- 0. 先取库存：key 不存在说明券不存在、或当前不在活动时间内
--    （预热时只把"进行中"的券写进 Redis，所以这里不能直接 tonumber）
local stock = redis.call('get', stockKey)
if not stock then
    -- 无库存记录，返回3
    return 3
end

-- 1. 判断用户是否已经下过单（一人一单）
--    必须放在库存判断之前：库存已被抢空时，重复下单的用户应该看到"重复下单"而不是"库存不足"
if(redis.call('sismember', orderKey, userId) == 1) then
    -- 重复下单，返回2
    return 2
end

-- 2. 判断库存是否充足
if(tonumber(stock) <= 0) then
    -- 库存不足，返回1
    return 1
end

-- 3. 扣减库存
redis.call('incrby', stockKey, -1)
-- 4. 把当前用户id加入集合，标记已经下单
redis.call('sadd', orderKey, userId)

-- 5. 成功，返回0
return 0
