-- keys: key dest
-- argv: score member [score member ...]
-- e.g. keys: "data:test:1:2" "data-sum:test:1:2"
--      argv: 10 a 15 b

local key = KEYS[1]
local dest = KEYS[2]
local sum = tonumber(redis.call('get', dest)) or 0

for i=1, #ARGV, 2 do
    local score = tonumber(ARGV[i]) or 0
    local member = ARGV[i + 1]
    local currentScore = tonumber(redis.call('zscore', key, member))

    if score ~= currentScore then
        sum = sum + score - (currentScore or 0)
        redis.call('zadd', key, score, member)
    end
end

redis.call('set', dest, sum)
