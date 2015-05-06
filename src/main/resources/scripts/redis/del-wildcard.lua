for i=1, #ARGV, 1 do
    for _, k in ipairs(redis.call("keys", ARGV[i])) do
        redis.call("del", k)
    end
end
