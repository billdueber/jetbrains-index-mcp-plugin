require_relative "base"

class SubService < BaseService
  include M

  def process
    "sub service"
  end
end