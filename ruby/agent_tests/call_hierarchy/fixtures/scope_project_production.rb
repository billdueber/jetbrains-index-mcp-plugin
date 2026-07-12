class ScopeTarget
  def target
    true
  end
end

class ProductionCaller
  def call_target
    ScopeTarget.new.target
  end
end
