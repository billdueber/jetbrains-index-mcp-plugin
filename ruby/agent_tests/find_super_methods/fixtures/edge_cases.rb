# Edge case: empty method body
class EmptyBody
  def no_op
  end
end

class EmptyBodyChild < EmptyBody
  def no_op
  end
end

# Edge case: singleton class syntax
class SingletonExample
  class << self
    def class_level
      true
    end
  end
end

# Edge case: module with extend
module Extendable
  def mixin_method
    "from module"
  end
end

class Extender
  extend Extendable

  def self.mixin_method
    "from class"
  end
end

# Edge case: method defined via define_method
class DynamicBase
  [:alpha, :beta].each do |m|
    define_method(m) { m.to_s }
  end
end

class DynamicSub < DynamicBase
  def alpha
    "overridden"
  end
end