module Greetable
  def greet
    "hello"
  end
end

class User
  include Greetable

  def greet
    "hi there"
  end
end