class BaseClass
  def self.find
    "base"
  end
end

class SubClass < BaseClass
  def self.find
    "sub"
  end
end