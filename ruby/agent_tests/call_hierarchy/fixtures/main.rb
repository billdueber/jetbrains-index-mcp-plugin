require_relative 'utils'

def greet(first, last)
  name = format_name(first, last)
  "Hello, #{name}!"
end

def announce(first, last)
  name = format_name(first, last)
  "Announcing #{name}!"
end
