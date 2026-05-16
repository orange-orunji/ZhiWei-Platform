--1.参数列表
--seckill:stock:11
local voucherId = ARGV[1]
--用户id
local userId = ARGV[2]
--订单id
local orderId = ARGV[3]

--2.定义key值
--库存key
local stockKey = "seckill:stock:" .. voucherId
--订单key
local orderKey = "seckill:order:" .. voucherId

--3.判断库存是否充足
local stock = redis.call('get',stockKey)
if ( stock == false or tonumber(stock) <= 0 )then
    return 1
end
--4.判断用户是否重复下单
--sismember判断用户列表是否存在当前用户
if(redis.call('sismember',orderKey,userId)==1) then
    return 2
end

--5.扣减库存
redis.call('incrby',stockKey,-1)
--6.记录用户并返回
redis.call('sadd',orderKey,userId)
redis.call('xadd','stream.orders','*','voucherId',voucherId,'userId',userId,'id',orderId)
return 0