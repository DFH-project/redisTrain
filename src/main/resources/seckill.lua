-- 1. 参数列表  1. 优惠卷id 2. 用户id
local voucherId = ARGV[1]
local userId = ARGV[2]

-- 2 数据key
-- 库存key  LUA脚本拼接使用的是 两个点
local stockKey = 'seckill:stock:' .. voucherId
-- 订单key
local orderKey = 'seckill:order:' .. voucherId

--3 脚本业务
-- 判断库存是否充足
if(tonumber(redis.call('get' ,stockKey)) <= 0 ) then
    -- 库存不足
    return 1
end
-- 判断用户是否下单
if(redis.call('sismember',orderKey,userId)==1) then
    -- 存在 说明重复下单
    return 2
end
-- 扣库存、保存用户
redis.call('incrby',stockKey,-1)
redis.call('sadd',orderKey,userId)

--发送消息到stream队列中
redis.call('xadd','stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId')
return 0
